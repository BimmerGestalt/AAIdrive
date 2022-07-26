package me.hufman.androidautoidrive.carapp.music.views

import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.Utils.etchAsInt
import io.bimmergestalt.idriveconnectkit.rhmi.*
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.RHMIModelMultiSetterData
import me.hufman.androidautoidrive.carapp.RHMIModelMultiSetterInt
import me.hufman.androidautoidrive.carapp.RHMIUtils.findAdjacentComponent
import me.hufman.androidautoidrive.carapp.music.MusicImageIDs
import me.hufman.androidautoidrive.carapp.music.TextScroller
import me.hufman.androidautoidrive.carapp.music.components.PlaylistItem
import me.hufman.androidautoidrive.carapp.music.components.ProgressGauge
import me.hufman.androidautoidrive.carapp.music.components.ProgressGaugeAudioState
import me.hufman.androidautoidrive.carapp.music.components.ProgressGaugeToolbarState
import me.hufman.androidautoidrive.music.*
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import me.hufman.androidautoidrive.utils.TimeUtils.formatTime
import me.hufman.androidautoidrive.utils.Utils

class PlaybackView(val state: RHMIState, val controller: MusicController, val carAppImages: Map<String, ByteArray>, val phoneAppResources: PhoneAppResources, val graphicsHelpers: GraphicsHelpers, val musicImageIDs: MusicImageIDs) {
	companion object {
		const val MUSIC_METADATA_MAX_LINE_LENGTH = 30
		const val AUDIOSTATE_PLAYLIST_MAX_LINE_LENGTH = 28
		const val INITIALIZATION_DEFERRED_TIMEOUT = 6000
		const val POSITION_ACTION_DEBOUNCE = 500
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.AudioHmiState || (
					state is RHMIState.ToolbarState &&
					state.componentsList.filterIsInstance<RHMIComponent.Gauge>().isNotEmpty() &&
					state.componentsList.filterIsInstance<RHMIComponent.Image>().filter {
						it.getModel() is RHMIModel.RaImageModel
					}.isNotEmpty()
					)
		}
	}

	val appTitleModel: RHMIModel.RaDataModel
	val appLogoModel: RHMIModel.RaImageModel?
	val albumArtBigComponent: RHMIComponent.Image?
	val albumArtSmallComponent: RHMIComponent.Image?
	val albumArtBigModel: RHMIModel.RaImageModel
	val albumArtSmallModel: RHMIModel.RaImageModel?
	val artistModel: RHMIModelMultiSetterData
	val albumModel: RHMIModelMultiSetterData
	val trackModel: RHMIModelMultiSetterData
	val gaugeModel: ProgressGauge
	val currentTimeModel: RHMIModelMultiSetterData
	val maximumTimeModel: RHMIModelMultiSetterData

	val queueToolbarButton: RHMIComponent.ToolbarButton
	val customActionButton: RHMIComponent.ToolbarButton
	var skipBackButton: RHMIComponent.ToolbarButton? = null
	var skipNextButton: RHMIComponent.ToolbarButton? = null
	val shuffleButton: RHMIComponent.ToolbarButton
	val repeatButton: RHMIComponent.ToolbarButton?

	val albumArtPlaceholderBig = carAppImages["${musicImageIDs.COVERART_LARGE}.png"]
	val albumArtPlaceholderSmall = carAppImages["${musicImageIDs.COVERART_SMALL}.png"]
	val grayscaleNoteIcon: Any

	var visible = false
	var initialized = false
	var initializationDeferredTime = System.currentTimeMillis() + INITIALIZATION_DEFERRED_TIMEOUT
	var displayedApp: MusicAppInfo? = null  // the app that was last redrawn
	var displayedSong: MusicMetadata? = null    // the song  that was last redrawn
	var displayedConnected: Boolean = false     // whether the controller was connected during redraw
	var isNewerIDrive: Boolean = false
	var isBuffering: Boolean = false
	var skipBackEnabled: Boolean = true
	var skipNextEnabled: Boolean = true
	var lastPositionActionTime: Long = 0

	var artistTextScroller: TextScroller = TextScroller("", 0)
	var albumTextScroller: TextScroller = TextScroller("", 0)
	var trackTextScroller: TextScroller = TextScroller("", 0)

	init {
		// discover widgets
		if (state is RHMIState.AudioHmiState) {
			appTitleModel = state.getTextModel()?.asRaDataModel()!!
			appLogoModel = null

			albumArtBigComponent = null
			albumArtSmallComponent = null
			albumArtBigModel = state.getCoverImageModel()?.asRaImageModel()!!
			albumArtSmallModel = null

			artistModel = RHMIModelMultiSetterData(listOf(state.getArtistTextModel()?.asRaDataModel()))
			albumModel = RHMIModelMultiSetterData(listOf(state.getAlbumTextModel()?.asRaDataModel()))
			trackModel = RHMIModelMultiSetterData(listOf(state.getTrackTextModel()?.asRaDataModel()))

			currentTimeModel = RHMIModelMultiSetterData(listOf(state.getCurrentTimeModel()?.asRaDataModel()))
			maximumTimeModel = RHMIModelMultiSetterData(listOf(state.getElapsingTimeModel()?.asRaDataModel()))
			gaugeModel = ProgressGaugeAudioState(state.getPlaybackProgressModel()?.asRaDataModel()!!)

			// playlist model populates the back/title/next section

			queueToolbarButton = state.toolbarComponentsList[2]
			customActionButton = state.toolbarComponentsList[3]
			shuffleButton = state.toolbarComponentsList[4]
			repeatButton = state.toolbarComponentsList[5]
		} else {
			state as RHMIState.ToolbarState
			appTitleModel = state.getTextModel()?.asRaDataModel()!!
			appLogoModel = state.componentsList.filterIsInstance<RHMIComponent.Image>().first {
				// The one single image which is visible in both wide and small screen modes
				val property = it.properties[RHMIProperty.PropertyId.POSITION_X.id]
				val smallPosition = (property as? RHMIProperty.LayoutBag)?.get(1)
				val widePosition = (property as? RHMIProperty.LayoutBag)?.get(0)
				(smallPosition is Int && smallPosition < 1900) &&
				(widePosition is Int && widePosition < 1900)
			}.getModel()?.asRaImageModel()!!

			// group the components into which widescreen state they are visible in
			// the layout hides the components by setting their X to 2000
			val smallComponents = state.componentsList.filter {
				val property = it.properties[RHMIProperty.PropertyId.POSITION_X.id]
				val smallPosition = (property as? RHMIProperty.LayoutBag)?.get(1)
				smallPosition is Int && smallPosition < 1900
			}
			val wideComponents = state.componentsList.filter {
				val property = it.properties[RHMIProperty.PropertyId.POSITION_X.id]
				val widePosition = (property as? RHMIProperty.LayoutBag)?.get(0)
				widePosition is Int && widePosition < 1900
			}

			// remember the two cover arts as separate images, to resize to the correct size in each
			albumArtBigComponent = wideComponents.filterIsInstance<RHMIComponent.Image>().first {
				(it.properties[RHMIProperty.PropertyId.HEIGHT.id]?.value as? Int ?: 0) == 320
			}
			albumArtBigModel = albumArtBigComponent.getModel()?.asRaImageModel()!!
			albumArtSmallComponent = smallComponents.filterIsInstance<RHMIComponent.Image>().first {
				(it.properties[RHMIProperty.PropertyId.HEIGHT.id]?.value as? Int ?: 0) == 200
			}
			albumArtSmallModel = albumArtSmallComponent.getModel()?.asRaImageModel()!!

			// set up model multisetters for duplicate components
			val artists = arrayOf(smallComponents, wideComponents).map { components ->
				val icon = components.firstOrNull { it.asImage()?.getModel()?.asImageIdModel()?.imageId == musicImageIDs.ARTIST }
				findAdjacentComponent(components, icon)
			}
			artistModel = RHMIModelMultiSetterData(artists.map { it?.asLabel()?.getModel()?.asRaDataModel() })

			val albums = arrayOf(smallComponents, wideComponents).map { components ->
				val icon = components.firstOrNull { it.asImage()?.getModel()?.asImageIdModel()?.imageId == musicImageIDs.ALBUM }
				findAdjacentComponent(components, icon)
			}
			albumModel = RHMIModelMultiSetterData(albums.map { it?.asLabel()?.getModel()?.asRaDataModel() })

			val titles = arrayOf(smallComponents, wideComponents).map { components ->
				val icon = components.firstOrNull { it.asImage()?.getModel()?.asImageIdModel()?.imageId == musicImageIDs.SONG }
				findAdjacentComponent(components, icon)
			}
			trackModel = RHMIModelMultiSetterData(titles.map { it?.asLabel()?.getModel()?.asRaDataModel() })

			val currentTimes = arrayOf(smallComponents, wideComponents).map { components ->
				components.filterIsInstance<RHMIComponent.Label>().dropLast(1).last()
			}
			currentTimeModel = RHMIModelMultiSetterData(currentTimes.map { it.asLabel()?.getModel()?.asRaDataModel() })
			val maxTimes = arrayOf(smallComponents, wideComponents).map { components ->
				components.filterIsInstance<RHMIComponent.Label>().last()
			}
			maximumTimeModel = RHMIModelMultiSetterData(maxTimes.map { it.asLabel()?.getModel()?.asRaDataModel() })
			val gauges = arrayOf(smallComponents, wideComponents).map { components ->
				components.filterIsInstance<RHMIComponent.Gauge>().first()
			}
			gaugeModel = ProgressGaugeToolbarState(RHMIModelMultiSetterInt(gauges.map { it.getModel()?.asRaIntModel() }))

			// remember the toolbar buttons for convenient redrawing of their status
			queueToolbarButton = state.toolbarComponentsList[2]
			customActionButton = state.toolbarComponentsList[4]
			shuffleButton = state.toolbarComponentsList[5]
			skipBackButton = state.toolbarComponentsList[6]
			skipNextButton = state.toolbarComponentsList[7]

			// repeat button is only available for Spotify (audioHmiState) due to lack of repeat button icon
			repeatButton = null
		}

		// memoize grayscale note icon
		val noteIcon = carAppImages["${musicImageIDs.SONG}.png"]
		grayscaleNoteIcon = if (noteIcon != null) {
			Utils.convertPngToGrayscale(noteIcon)
		} else {
			""
		}
	}

	/**
	 * Link up the event handlers to the respective destination states
	 */
	fun initWidgets(appSwitcherView: AppSwitcherView, enqueuedView: EnqueuedView, browseView: BrowseView, customActionsView: CustomActionsView) {
		state as RHMIState.ToolbarState
		state.focusCallback = FocusCallback { focused ->
			visible = focused
			if (focused) {
				show()
			}
		}

		// link up the actions in the buttons
		val buttons = state.toolbarComponentsList
		buttons[0].getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = appSwitcherView.state.id
		buttons[1].getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
			browseView.clearPages()
			val page = browseView.pushBrowsePage(null)
			buttons[1].getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = page.state.id
		}
		buttons[2].getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = enqueuedView.state.id
		customActionButton.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = customActionsView.state.id

		if (state is RHMIState.AudioHmiState) {
			state.getProgressAction()?.asRAAction()?.rhmiActionCallback = RHMIActionCallback { args ->
				if (args?.containsKey(45.toByte()) == true && lastPositionActionTime + POSITION_ACTION_DEBOUNCE < System.currentTimeMillis()) {
					val newPosition = etchAsInt(args[45.toByte()])
					controller.seekTo(controller.getPlaybackPosition().maximumPosition * newPosition / 100)
					lastPositionActionTime = System.currentTimeMillis()
				}
			}
			state.getArtistAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
				browseView.clearPages()
				val page = browseView.pushBrowsePage(null)
				state.getArtistAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = page.state.id
			}
			state.getAlbumAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
				browseView.clearPages()
				val page = browseView.pushBrowsePage(null)
				state.getAlbumAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = page.state.id
			}
		}
	}

	/**
	 * Apply the UI settings of the playback state
	 * as a separate function that can be run later
	 */
	fun initWidgetsLater() {
		state as RHMIState.ToolbarState

		val buttons = state.toolbarComponentsList
		// shortcuts to other windows
		buttons[0].getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_APPLIST_TITLE
		buttons[1].getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_BROWSE_TITLE
		buttons[2].getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_QUEUE_TITLE
		buttons[2].setEnabled(false)

		// setting the actions button icon since the button has a book icon by default
		customActionButton.getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_CUSTOMACTIONS_TITLE
		customActionButton.getImageModel()?.asImageIdModel()?.imageId = musicImageIDs.ACTIONS
		customActionButton.setEnabled(false)

		shuffleButton.getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_TURN_SHUFFLE_UNAVAILABLE
		shuffleButton.getImageModel()?.asImageIdModel()?.imageId = musicImageIDs.SHUFFLE_OFF
		shuffleButton.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
			controller.toggleShuffle()
			// this button has the same TargetModel as the Toolbar PlaybackView's Actions button
			// so we have to throw Abort to not continue to that screen
			throw RHMIActionAbort()
		}

		skipBackButton?.getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_SKIP_PREVIOUS
		skipBackButton?.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback { controller.skipToPrevious() }

		skipNextButton?.getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_SKIP_NEXT
		skipNextButton?.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback { controller.skipToNext() }

		// try to hide extra buttons
		if (state !is RHMIState.AudioHmiState) {
			// the book icon
			try {
				buttons[3].getImageModel()?.asImageIdModel()?.imageId = 0
			} catch (e: BMWRemoting.ServiceException) {
				buttons[3].setVisible(false)
			}
			buttons[3].setSelectable(false)
		}
		if (state is RHMIState.AudioHmiState) {
			// weird that official Spotify doesn't need to do this
			val artistIcon = carAppImages["${musicImageIDs.ARTIST}.png"]
			if (artistIcon != null) {
				state.getArtistImageModel()?.asRaImageModel()?.value = Utils.convertPngToGrayscale(artistIcon)
			}
			val albumIcon = carAppImages["${musicImageIDs.ALBUM}.png"]
			if (albumIcon != null) {
				state.getAlbumImageModel()?.asRaImageModel()?.value = Utils.convertPngToGrayscale(albumIcon)
			}

			repeatButton?.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionCallback { controller.toggleRepeat() }
			repeatButton?.getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_TURN_REPEAT_UNAVAILABLE
			repeatButton?.getImageModel()?.asImageIdModel()?.imageId = musicImageIDs.REPEAT_OFF

			if (displayedSong == null) {
				redrawAudiostatePlaylist("")
			}

			state.getPlayListFocusRowModel()?.asRaIntModel()?.value = 1
			state.getPlayListAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index ->
				when (index) {
					0 -> controller.skipToPrevious()
					1 -> controller.seekTo(0)
					2 -> controller.skipToNext()
				}
			}
		}

		queueToolbarButton.setProperty(RHMIProperty.PropertyId.BOOKMARKABLE, true)
		customActionButton.setProperty(RHMIProperty.PropertyId.BOOKMARKABLE, true)

		initialized = true
	}

	fun show() {
		redraw()

		if (state is RHMIState.AudioHmiState) {
			// set the highlight to the middle when showing the window
			state.getPlayListFocusRowModel()?.asRaIntModel()?.value = 1
		}
	}

	/** Clear out the cache of displayed items for a full redraw */
	fun forgetDisplayedInfo() {
		displayedApp = null
		displayedSong = null
		initialized = false     // important for re-initializing the AudioHmiState artist/album icons
	}

	/** Any updates that should happen in the background */
	fun backgroundRedraw() {
		if (!initialized && initializationDeferredTime < System.currentTimeMillis()) {
			try {
				initWidgetsLater()
			} catch (e: BMWRemoting.ServiceException) {
				// something went wrong during background initialization
				// but don't crash, instead wait to initialize on first view
			}
		}

		// Redraw these in the background, for AudioHmiState's global metadata
		if (state is RHMIState.AudioHmiState) {
			if (displayedSong != controller.getMetadata() ||
					displayedConnected != controller.isConnected()) {
				try {
					redrawSong()
				} catch (e: BMWRemoting.ServiceException) {
					// something went wrong during background update
					// sometimes seen when updating the AudioHmiState Playlist model
					// but don't crash, instead continue on and try to redraw again on next view
					// analytics says only a single user (ID4 running ID5 AudioHmiState somehow)
					// experiences this, so we'll accept the inefficiency of repeatedly trying to update
				}
			} else {
				// if the song is the same, update any scrolling text
				redrawLongTitles()
			}
			try {
				redrawPosition()
			} catch (e: BMWRemoting.ServiceException) {
				// something went wrong during background update
				// but don't crash about it
			}
		}
	}

	/** Any updates that should happen while the screen is displayed */
	fun redraw() {
		if (!initialized) {
			initWidgetsLater()
		}

		if (displayedApp != controller.currentAppInfo) {
			redrawApp()
		}
		if (displayedSong != controller.getMetadata() ||
				displayedConnected != controller.isConnected()) {
			redrawSong()
		} else {
			redrawLongTitles()
		}
		redrawPosition()
		redrawQueueButton()
		redrawShuffleButton()
		redrawRepeatButton()
		redrawActions()
	}

	/**
	 * Redraw long titles with current state of the text scrolling
	 */
	private fun redrawLongTitles() {
		artistModel.value = artistTextScroller.getText()
		albumModel.value = albumTextScroller.getText()
		val trackText = trackTextScroller.getText()
		redrawAudiostatePlaylist(trackText)
		trackModel.value = trackText
	}

	private fun redrawApp() {
		val app = controller.currentAppInfo ?: return
		appTitleModel.value = app.name
		val image = graphicsHelpers.compress(app.icon, 48, 48)
		appLogoModel?.value = image
		displayedApp = app
	}

	private fun redrawSong() {
		val song = controller.getMetadata()

		val artistTitle = if (controller.isConnected()) {
			UnicodeCleaner.clean(song?.artist ?: "")
		} else { L.MUSIC_DISCONNECTED }
		artistTextScroller = TextScroller(artistTitle, MUSIC_METADATA_MAX_LINE_LENGTH)

		val albumTitle = UnicodeCleaner.clean(song?.album ?: "")
		albumTextScroller = TextScroller(albumTitle, MUSIC_METADATA_MAX_LINE_LENGTH)

		val trackTitle = UnicodeCleaner.clean(song?.title ?: "")
		val trackMaxLineLength = if (state is RHMIState.AudioHmiState) {
			AUDIOSTATE_PLAYLIST_MAX_LINE_LENGTH
		} else {
			MUSIC_METADATA_MAX_LINE_LENGTH
		}
		trackTextScroller = TextScroller(trackTitle, trackMaxLineLength)

		redrawLongTitles()

		val songCoverArt = song?.coverArt
		if (songCoverArt != null) {
			albumArtBigModel.value = graphicsHelpers.compress(songCoverArt, 320, 320, quality = 65)
			if (albumArtSmallModel != null) {
				albumArtSmallModel.value = graphicsHelpers.compress(songCoverArt, 200, 200, quality = 65)
			}
			albumArtBigComponent?.setVisible(true)
			albumArtSmallComponent?.setVisible(true)
		} else if (song?.coverArtUri != null) {
			try {
				val coverArt = phoneAppResources.getUriDrawable(song.coverArtUri)
				albumArtBigModel.value = graphicsHelpers.compress(coverArt, 320, 320, quality = 65)
				if (albumArtSmallModel != null) {
					albumArtSmallModel.value = graphicsHelpers.compress(coverArt, 200, 200, quality = 65)
				}
				albumArtBigComponent?.setVisible(true)
				albumArtSmallComponent?.setVisible(true)
			} catch (e: Exception) {
				showPlaceholderCoverart()
			}
		} else {
			showPlaceholderCoverart()
		}

		// update the audio state playlist
		redrawAudiostatePlaylist(trackTitle)

		displayedSong = song
		displayedConnected = controller.isConnected()
	}

	private fun redrawAudiostatePlaylist(title: String) {
		if (state is RHMIState.AudioHmiState) {
			val playlistModel = state.getPlayListModel()?.asRaListModel()
			val playlist = RHMIModel.RaListModel.RHMIListConcrete(10)
			playlist.addRow(PlaylistItem(false, skipBackEnabled, BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, musicImageIDs.SKIP_BACK), L.MUSIC_SKIP_PREVIOUS))
			playlist.addRow(PlaylistItem(isBuffering, true, grayscaleNoteIcon, title))
			playlist.addRow(PlaylistItem(false, skipNextEnabled, BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, musicImageIDs.SKIP_NEXT), L.MUSIC_SKIP_NEXT))
			playlistModel?.asRaListModel()?.setValue(playlist, 0, 3, 3)
		}
	}

	private fun showPlaceholderCoverart() {
		if (albumArtPlaceholderBig != null) {
			albumArtBigModel.value = albumArtPlaceholderBig
		} else {
			albumArtBigComponent?.setVisible(false)
		}
		if (albumArtPlaceholderSmall != null) {
			albumArtSmallModel?.value = albumArtPlaceholderSmall
		} else {
			albumArtSmallComponent?.setVisible(false)
		}
	}

	private fun redrawQueueButton() {
		val queue = controller.getQueue()?.songs
		queueToolbarButton.setEnabled(queue?.isNotEmpty() == true)
	}

	private fun redrawActions() {
		val customactions = controller.getCustomActions()
		customActionButton.setEnabled(customactions.isNotEmpty())

		// redraw if the skip actions changed status
		// the AudioState playlist isn't cached, so we have to track when to redraw
		if (
			skipBackEnabled != controller.isSupportedAction(MusicAction.SKIP_TO_PREVIOUS) ||
			skipNextEnabled != controller.isSupportedAction(MusicAction.SKIP_TO_NEXT)
		) {
			skipBackEnabled = controller.isSupportedAction(MusicAction.SKIP_TO_PREVIOUS)
			skipNextEnabled = controller.isSupportedAction(MusicAction.SKIP_TO_NEXT)

			skipBackButton?.setEnabled(skipBackEnabled)
			skipNextButton?.setEnabled(skipNextEnabled)

			redrawAudiostatePlaylist(controller.getMetadata()?.title ?: "")
		}
	}


	private fun redrawShuffleButton() {
		if (controller.isSupportedAction(MusicAction.SET_SHUFFLE_MODE)) {
			if (controller.isShuffling()) {
				shuffleButton.getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_TURN_SHUFFLE_OFF
				shuffleButton.getImageModel()?.asImageIdModel()?.imageId = musicImageIDs.SHUFFLE_ON
			} else {
				shuffleButton.getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_TURN_SHUFFLE_ON
				shuffleButton.getImageModel()?.asImageIdModel()?.imageId = musicImageIDs.SHUFFLE_OFF
			}
			shuffleButton.setEnabled(true)
			shuffleButton.setVisible(true)
		} else {
			if (state is RHMIState.AudioHmiState) {
				shuffleButton.setEnabled(false)
				shuffleButton.getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_TURN_SHUFFLE_UNAVAILABLE
				shuffleButton.getImageModel()?.asImageIdModel()?.imageId = musicImageIDs.SHUFFLE_OFF
			}
			else if (isNewerIDrive) {   // Audioplayer layout on id5
				shuffleButton.setVisible(false)
			} else {                    // Audioplayer on id4
				try {
					shuffleButton.getImageModel()?.asImageIdModel()?.imageId = 0
				} catch (e: BMWRemoting.ServiceException) {
					isNewerIDrive = true
					shuffleButton.setVisible(false)

					// the car has cleared the icon even though it threw an exception
					// so set the icon to a valid imageId again
					// to make sure the idempotent layer properly sets the icon in the future
					shuffleButton.getImageModel()?.asImageIdModel()?.imageId = musicImageIDs.SONG
				}
			}
		}
	}

	private fun redrawRepeatButton() {
		if (repeatButton != null && controller.isSupportedAction(MusicAction.SET_REPEAT_MODE)) {
			if (controller.getRepeatMode() == RepeatMode.ALL) {
				repeatButton.getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_TURN_REPEAT_ONE_ON
				repeatButton.getImageModel()?.asImageIdModel()?.imageId = musicImageIDs.REPEAT_ALL_ON
			} else if (controller.getRepeatMode() == RepeatMode.ONE) {
				repeatButton.getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_TURN_REPEAT_OFF
				repeatButton.getImageModel()?.asImageIdModel()?.imageId = musicImageIDs.REPEAT_ONE_ON
			} else {
				repeatButton.getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_TURN_REPEAT_ALL_ON
				repeatButton.getImageModel()?.asImageIdModel()?.imageId = musicImageIDs.REPEAT_OFF
			}
			repeatButton.setEnabled(true)
			repeatButton.setVisible(true)
		} else {
			repeatButton?.setEnabled(false)
			repeatButton?.getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_TURN_REPEAT_UNAVAILABLE
			repeatButton?.getImageModel()?.asImageIdModel()?.imageId = musicImageIDs.REPEAT_OFF
		}
	}

	private fun redrawPosition() {
		val progress = controller.getPlaybackPosition()
		if (isBuffering != progress.isBuffering) {
			isBuffering = progress.isBuffering
			redrawAudiostatePlaylist(displayedSong?.title ?: "")
		}
		if (state is RHMIState.AudioHmiState && progress.maximumPosition <= 0) {
			// hide the progress bar from the sidebar
			gaugeModel.value = 0
			currentTimeModel.value = ""
			maximumTimeModel.value = ""
		} else {
			if (progress.maximumPosition <= 0) {
				gaugeModel.value = 50
			} else {
				gaugeModel.value = (100 * progress.getPosition() / progress.maximumPosition).toInt()
			}
			if (progress.isPaused && System.currentTimeMillis() % 1000 >= 500) {
				currentTimeModel.value = "   :  "
			} else {
				currentTimeModel.value = formatTime(progress.getPosition())
			}
			maximumTimeModel.value = formatTime(progress.maximumPosition)
		}
	}
}