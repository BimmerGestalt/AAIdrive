package me.hufman.androidautoidrive.carapp.music.views

import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.GraphicsHelpers
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.TimeUtils.formatTime
import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.carapp.RHMIModelMultiSetterData
import me.hufman.androidautoidrive.carapp.RHMIModelMultiSetterInt
import me.hufman.androidautoidrive.findAdjacentComponent
import me.hufman.androidautoidrive.music.MusicAction
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.idriveconnectionkit.rhmi.*

private const val IMAGEID_COVERART_SMALL = 146
private const val IMAGEID_COVERART_LARGE = 147
private const val IMAGEID_ARTIST = 150
private const val IMAGEID_ALBUM = 148
private const val IMAGEID_SONG = 152
private const val IMAGEID_SHUFFLE_OFF = 158
private const val IMAGEID_SHUFFLE_ON = 151

class PlaybackView(val state: RHMIState, val controller: MusicController, carAppImages: Map<String, ByteArray>, val phoneAppResources: PhoneAppResources, val graphicsHelpers: GraphicsHelpers) {
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.ToolbarState &&
					state.componentsList.filterIsInstance<RHMIComponent.Gauge>().isNotEmpty() &&
					state.componentsList.filterIsInstance<RHMIComponent.Image>().filter {
						it.getModel() is RHMIModel.RaImageModel
					}.isNotEmpty()
		}
	}

	val appTitleModel: RHMIModel.RaDataModel
	val appLogoModel: RHMIModel.RaImageModel
	val albumArtBigComponent: RHMIComponent.Image
	val albumArtSmallComponent: RHMIComponent.Image
	val albumArtBigModel: RHMIModel.RaImageModel
	val albumArtSmallModel: RHMIModel.RaImageModel
	val artistModel: RHMIModelMultiSetterData
	val albumModel: RHMIModelMultiSetterData
	val trackModel: RHMIModelMultiSetterData
	val gaugeModel: RHMIModelMultiSetterInt
	val currentTimeModel: RHMIModelMultiSetterData
	val maximumTimeModel: RHMIModelMultiSetterData

	val queueToolbarButton: RHMIComponent.ToolbarButton
	val customActionButton: RHMIComponent.ToolbarButton
	val skipBackButton: RHMIComponent.ToolbarButton
	val skipNextButton: RHMIComponent.ToolbarButton
	val shuffleButton: RHMIComponent.ToolbarButton

	val albumArtPlaceholderBig = carAppImages["$IMAGEID_COVERART_LARGE.png"]
	val albumArtPlaceholderSmall = carAppImages["$IMAGEID_COVERART_SMALL.png"]

	var displayedApp: MusicAppInfo? = null  // the app that was last redrawn
	var displayedSong: MusicMetadata? = null    // the song  that was last redrawn
	var displayedConnected: Boolean = false     // whether the controller was connected during redraw
	var isNewerIDrive: Boolean = false

	init {
		// discover widgets
		state as RHMIState.ToolbarState

		appTitleModel = state.getTextModel()?.asRaDataModel()!!
		appLogoModel = state.componentsList.filterIsInstance<RHMIComponent.Image>().filter {
			var property = it.properties[20]
			val smallPosition = (property as? RHMIProperty.LayoutBag)?.get(1)
			val widePosition = (property as? RHMIProperty.LayoutBag)?.get(0)
			(smallPosition is Int && smallPosition < 1900) &&
					(widePosition is Int && widePosition < 1900)
		}.first().getModel()?.asRaImageModel()!!

		val smallComponents = state.componentsList.filter {
			val property = it.properties[20]
			val smallPosition = (property as? RHMIProperty.LayoutBag)?.get(1)
			smallPosition is Int && smallPosition < 1900
		}
		val wideComponents = state.componentsList.filter {
			val property = it.properties[20]
			val widePosition = (property as? RHMIProperty.LayoutBag)?.get(0)
			widePosition is Int && widePosition < 1900
		}
		albumArtBigComponent = wideComponents.filterIsInstance<RHMIComponent.Image>().first {
			(it.properties[10]?.value as? Int ?: 0) == 320
		}
		albumArtBigModel = albumArtBigComponent.getModel()?.asRaImageModel()!!
		albumArtSmallComponent = smallComponents.filterIsInstance<RHMIComponent.Image>().first {
			(it.properties[10]?.value as? Int ?: 0) == 200
		}
		albumArtSmallModel = albumArtSmallComponent.getModel()?.asRaImageModel()!!

		val artists = arrayOf(smallComponents, wideComponents).map { components ->
			findAdjacentComponent(components) { it.asImage()?.getModel()?.asImageIdModel()?.imageId == IMAGEID_ARTIST}
		}
		artistModel = RHMIModelMultiSetterData(artists.map { it?.asLabel()?.getModel()?.asRaDataModel() })

		val albums = arrayOf(smallComponents, wideComponents).map { components ->
			findAdjacentComponent(components) { it.asImage()?.getModel()?.asImageIdModel()?.imageId == IMAGEID_ALBUM}
		}
		albumModel = RHMIModelMultiSetterData(albums.map { it?.asLabel()?.getModel()?.asRaDataModel() })

		val titles = arrayOf(smallComponents, wideComponents).map { components ->
			findAdjacentComponent(components) { it.asImage()?.getModel()?.asImageIdModel()?.imageId == IMAGEID_SONG}
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
		gaugeModel = RHMIModelMultiSetterInt(gauges.map { it.getModel()?.asRaIntModel() })

		queueToolbarButton = state.toolbarComponentsList[2]
		customActionButton = state.toolbarComponentsList[4]
		shuffleButton = state.toolbarComponentsList[5]
		skipBackButton = state.toolbarComponentsList[6]
		skipNextButton = state.toolbarComponentsList[7]
	}

	fun initWidgets(appSwitcherView: AppSwitcherView, enqueuedView: EnqueuedView, browseView: BrowseView, customActionsView: CustomActionsView) {
		state as RHMIState.ToolbarState

		val buttons = state.toolbarComponentsList
		buttons[0].getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_APPLIST_TITLE
		buttons[0].getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = appSwitcherView.state.id

		buttons[1].getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_BROWSE_TITLE
		buttons[1].getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
			browseView.clearPages()
			val page = browseView.pushBrowsePage(null)
			buttons[1].getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = page.state.id
		}

		buttons[2].getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_QUEUE_TITLE
		buttons[2].setEnabled(false)
		buttons[2].getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = enqueuedView.state.id

		// the book icon
		try {
			buttons[3].getImageModel()?.asImageIdModel()?.imageId = 0
		} catch (e: BMWRemoting.ServiceException) {
			buttons[3].setVisible(false)
		}
		buttons[3].setSelectable(false)

		buttons[4].getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_CUSTOMACTIONS_TITLE
		buttons[4].setEnabled(false)
		buttons[4].getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = customActionsView.state.id

		buttons[5].getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_TURN_SHUFFLE_ON
		buttons[5].getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback { controller.toggleShuffle() }

		buttons[6].getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_SKIP_PREVIOUS
		buttons[6].getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback { controller.skipToPrevious() }

		buttons[7].getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_SKIP_NEXT
		buttons[7].getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback { controller.skipToNext() }
	}

	fun show() {
		redraw()
	}

	fun redraw() {
		if (displayedApp != controller.currentAppInfo) {
			redrawApp()
		}
		if (displayedSong != controller.getMetadata() ||
				displayedConnected != controller.isConnected()) {
			redrawSong()
		}
		redrawQueueButton()
		redrawShuffleButton()
		redrawActions()
		redrawPosition()
	}

	private fun redrawApp() {
		val app = controller.currentAppInfo ?: return
		appTitleModel.value = app.name
		val image = graphicsHelpers.compress(app.icon, 48, 48)
		appLogoModel.value = image
		displayedApp = app
	}

	private fun redrawSong() {
		val song = controller.getMetadata()
		artistModel.value = if (controller.isConnected()) {
			UnicodeCleaner.clean(song?.artist ?: "")
		} else { L.MUSIC_DISCONNECTED }
		albumModel.value = UnicodeCleaner.clean(song?.album ?: "")
		trackModel.value = UnicodeCleaner.clean(song?.title ?: "")
		if (song?.coverArt != null) {
			albumArtBigModel.value = graphicsHelpers.compress(song.coverArt, 320, 320, quality = 65)
			albumArtSmallModel.value = graphicsHelpers.compress(song.coverArt, 200, 200, quality = 65)
			albumArtBigComponent.setVisible(true)
			albumArtSmallComponent.setVisible(true)
		} else if (song?.coverArtUri != null) {
			try {
				val coverArt = phoneAppResources.getUriDrawable(song.coverArtUri)
				albumArtBigModel.value = graphicsHelpers.compress(coverArt, 320, 320, quality = 65)
				albumArtSmallModel.value = graphicsHelpers.compress(coverArt, 200, 200, quality = 65)
				albumArtBigComponent.setVisible(true)
				albumArtSmallComponent.setVisible(true)
			} catch (e: Exception) {
				showPlaceholderCoverart()
			}
		} else {
			showPlaceholderCoverart()
		}

		displayedSong = song
		displayedConnected = controller.isConnected()
	}

	private fun showPlaceholderCoverart() {
		if (albumArtPlaceholderBig != null) {
			albumArtBigModel.value = albumArtPlaceholderBig
		} else {
			albumArtBigComponent.setVisible(false)
		}
		if (albumArtPlaceholderSmall != null) {
			albumArtSmallModel.value = albumArtPlaceholderSmall
		} else {
			albumArtSmallComponent.setVisible(false)
		}
	}

	private fun redrawQueueButton() {
		val queue = controller.getQueue()
		queueToolbarButton.setEnabled(queue?.isNotEmpty() == true)
	}

	private fun redrawActions() {
		val customactions = controller.getCustomActions()
		customActionButton.setEnabled(customactions.isNotEmpty())

		skipBackButton.setEnabled(controller.isSupportedAction(MusicAction.SKIP_TO_PREVIOUS))
		skipNextButton.setEnabled(controller.isSupportedAction(MusicAction.SKIP_TO_NEXT))
	}

	private fun redrawShuffleButton() {
		if (controller.isSupportedAction(MusicAction.SET_SHUFFLE_MODE)) {
			if (controller.isShuffling()) {
				shuffleButton.getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_TURN_SHUFFLE_OFF
				shuffleButton.getImageModel()?.asImageIdModel()?.imageId = IMAGEID_SHUFFLE_ON
			} else {
				shuffleButton.getTooltipModel()?.asRaDataModel()?.value = L.MUSIC_TURN_SHUFFLE_ON
				shuffleButton.getImageModel()?.asImageIdModel()?.imageId = IMAGEID_SHUFFLE_OFF
			}
			shuffleButton.setVisible(true)
		} else {
			if (isNewerIDrive) {
				shuffleButton.setVisible(false)
			} else {
				try {
					shuffleButton.getImageModel()?.asImageIdModel()?.imageId = 0
				} catch (e: BMWRemoting.ServiceException) {
					isNewerIDrive = true
					shuffleButton.setVisible(false)

					// the car has cleared the icon even though it threw an exception
					// so set the icon to a valid imageId again
					// to make sure the idempotent layer properly sets the icon in the future
					shuffleButton.getImageModel()?.asImageIdModel()?.imageId = IMAGEID_SONG
				}
			}
		}
	}

	private fun redrawPosition() {
		val progress = controller.getPlaybackPosition()
		if (progress.maximumPosition > 0) {
			gaugeModel.value = (100 * progress.getPosition() / progress.maximumPosition).toInt()
		} else {
			gaugeModel.value = 50
		}
		maximumTimeModel.value = formatTime(progress.maximumPosition)
		currentTimeModel.value = if (progress.playbackPaused && System.currentTimeMillis() % 1000 >= 500) {
			"   :  "
		} else {
			formatTime(progress.getPosition())
		}
	}

}