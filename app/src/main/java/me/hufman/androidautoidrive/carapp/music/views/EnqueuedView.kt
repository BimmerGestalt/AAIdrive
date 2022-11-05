package me.hufman.androidautoidrive.carapp.music.views

import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.rhmi.*
import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.music.MusicImageIDs
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.music.QueueMetadata
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import me.hufman.androidautoidrive.utils.truncate
import kotlin.math.max
import kotlin.math.min

class EnqueuedView(val state: RHMIState, val musicController: MusicController, val graphicsHelpers: GraphicsHelpers, val musicImageIDs: MusicImageIDs) {
	companion object {
		// current default row width only supports 22 chars before rolling over
		private const val ROW_LINE_MAX_LENGTH = 22
		private const val TITLE_MAX_LENGTH = 35

		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty() &&
					state.componentsList.filterIsInstance<RHMIComponent.Image>().any {
						!it.properties.containsKey(RHMIProperty.PropertyId.POSITION_X.id)
					} &&
					state.componentsList.filterIsInstance<RHMIComponent.Separator>().isEmpty()
		}
	}

	val listComponent: RHMIComponent.List
	val queueImageComponent: RHMIComponent.Image
	val titleLabelComponent: RHMIComponent.Label
	val subtitleLabelComponent: RHMIComponent.Label

	var visible = false
	var currentSong: MusicMetadata? = null
	val songsList = ArrayList<MusicMetadata>()
	val songsEmptyList = RHMIModel.RaListModel.RHMIListConcrete(3)
	var queueMetadata: QueueMetadata? = null
	var visibleRows: List<MusicMetadata> = emptyList()
	var visibleRowsOriginalMusicMetadata: List<MusicMetadata> = emptyList()
	var selectedIndex: Int = 0

	var songsListAdapter = object: RHMIModel.RaListModel.RHMIListAdapter<MusicMetadata>(4, songsList) {
		override fun convertRow(index: Int, item: MusicMetadata): Array<Any> {
			val checkmark = if (item.queueId == currentSong?.queueId) BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, musicImageIDs.CHECKMARK) else ""

			val coverArt = item.coverArt
			val coverArtImage = if (coverArt != null) graphicsHelpers.compress(coverArt, 90, 90, quality = 30) else ""

			val title = UnicodeCleaner.clean(item.title ?: "")
			val artist = UnicodeCleaner.clean(item.artist ?: "")
			val songMetaDataText = if (artist.isNotBlank()) {
				"${title.truncate(ROW_LINE_MAX_LENGTH)}\n$artist"
			} else {
				title
			}

			return arrayOf(
					checkmark,
					coverArtImage,
					"",
					songMetaDataText
			)
		}
	}

	init {
		state as RHMIState.PlainState

		listComponent = state.componentsList.filterIsInstance<RHMIComponent.List>().first()
		queueImageComponent = state.componentsList.filterIsInstance<RHMIComponent.Image>().first {
			!it.properties.containsKey(RHMIProperty.PropertyId.POSITION_X.id)
		}
		titleLabelComponent = state.componentsList.filterIsInstance<RHMIComponent.Label>()[0]
		subtitleLabelComponent = state.componentsList.filterIsInstance<RHMIComponent.Label>()[1]

		songsEmptyList.addRow(arrayOf("", "", "", L.MUSIC_QUEUE_EMPTY))
	}

	fun initWidgets(playbackView: PlaybackView) {
		state.focusCallback = FocusCallback { focused ->
			visible = focused
			if (focused) {
				show()
			}
		}
		queueImageComponent.setProperty(RHMIProperty.PropertyId.WIDTH, 180)
		titleLabelComponent.setProperty(RHMIProperty.PropertyId.CUTTYPE, 0)
		subtitleLabelComponent.setProperty(RHMIProperty.PropertyId.CUTTYPE, 0)

		// this is required for pagination system
		listComponent.setProperty(RHMIProperty.PropertyId.VALID, false)

		listComponent.setVisible(true)
		listComponent.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH, "57,90,10,*")
		listComponent.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = playbackView.state.id
		listComponent.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { onClick(it) }
		listComponent.getSelectAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { onSelectAction(it) }
	}

	fun show() {
		val newQueueMetadata = musicController.getQueue()

		// same queue as before, just select currently playing song
		if (queueMetadata != null && queueMetadata == newQueueMetadata) {
			showCurrentlyPlayingSong(true)
			setSelectionToCurrentSong()
			return
		}

		queueMetadata = newQueueMetadata
		songsList.clear()
		val songs = queueMetadata?.songs ?: emptyList()
		if (songs.any {it.coverArt != null || it.coverArtUri != null}) {
			listComponent.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH, "57,90,10,*")
		} else {
			listComponent.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH, "57,0,10,*")
		}
		if (songs.isNotEmpty()) {
			listComponent.setEnabled(true)
			listComponent.setSelectable(true)
			songsList.addAll(songs)
			showList(0)
			listComponent.requestDataCallback = RequestDataCallback { startIndex, numRows ->
				showList(startIndex, numRows)

				val endIndex = if (startIndex + numRows >= songsList.size) songsList.size - 1 else startIndex + numRows
				visibleRows = songsListAdapter.realData.subList(startIndex, endIndex + 1).toMutableList()
				visibleRowsOriginalMusicMetadata = visibleRows.map { MusicMetadata.copy(it) }
			}

			showCurrentlyPlayingSong(true)
			setSelectionToCurrentSong()
		} else {
			listComponent.setEnabled(false)
			listComponent.setSelectable(false)
			listComponent.getModel()?.setValue(songsEmptyList, 0, songsEmptyList.height, songsEmptyList.height)
		}

		val queueTitle = UnicodeCleaner.clean(queueMetadata?.title ?: "")
		val queueSubtitle = UnicodeCleaner.clean(queueMetadata?.subtitle ?: "")
		if (queueTitle.isBlank() && queueSubtitle.isBlank()) {
			state.getTextModel()?.asRaDataModel()?.value = L.MUSIC_QUEUE_TITLE
		} else {
			state.getTextModel()?.asRaDataModel()?.value = "$queueTitle - $queueSubtitle"
		}

		val queueCoverArt = queueMetadata?.coverArt
		if (queueCoverArt != null) {
			queueImageComponent.setVisible(true)
			queueImageComponent.getModel()?.asRaImageModel()?.value = graphicsHelpers.compress(queueCoverArt, 180, 180, quality = 60)
			titleLabelComponent.getModel()?.asRaDataModel()?.value = queueTitle.truncate(TITLE_MAX_LENGTH)
			subtitleLabelComponent.getModel()?.asRaDataModel()?.value = queueSubtitle.truncate(TITLE_MAX_LENGTH)
		}
		else {
			titleLabelComponent.getModel()?.asRaDataModel()?.value = ""
			subtitleLabelComponent.getModel()?.asRaDataModel()?.value = ""
			queueImageComponent.setVisible(false)
		}
	}

	fun forgetDisplayedInfo() {
		queueMetadata = null
	}

	fun redraw() {
		// need a full redraw if the queue is different or has been modified
		if (musicController.getQueue() != queueMetadata) {
			show()
			return
		}

		showCurrentlyPlayingSong(false)

		// redraw all currently visible rows if one of them has a cover art that was retrieved
		for ((index, metadata) in visibleRowsOriginalMusicMetadata.withIndex()) {
			if (metadata != visibleRows[index]) {
				// can only see roughly 5 rows
				showList(max(0, selectedIndex - 4), 8)
				break
			}
		}
	}

	/**
	 * Sets the list selection to the current song.
	 */
	private fun setSelectionToCurrentSong() {
		val index = songsList.indexOfFirst { it.queueId == currentSong?.queueId }
		if (index >= 0) {
			state.app.events.values.firstOrNull { it is RHMIEvent.FocusEvent }?.triggerEvent(
					mapOf(0.toByte() to listComponent.id, 41.toByte() to index)
			)
			onSelectAction(index)
		}
	}

	/**
	 * On select action callback. This is called every time the user scrolls in the queue list component.
	 */
	private fun onSelectAction(index: Int) {
		selectedIndex = index

		if (index != 0) {
			titleLabelComponent.setVisible(false)
			subtitleLabelComponent.setVisible(false)
		} else {
			titleLabelComponent.setVisible(true)
			subtitleLabelComponent.setVisible(true)
		}
	}

	/**
	 * Shows the list component content from the start index for the specified number of rows.
	 */
	private fun showList(startIndex: Int = 0, numRows: Int = 10) {
		val updatedList = ArrayList(songsList.subList(max(0, startIndex), min(songsList.size, startIndex + numRows)))
		if (updatedList.any {it.coverArt != null || it.coverArtUri != null}) {
			listComponent.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH, "57,90,10,*")
		}   // don't collapse the column if this window of data happens to not have coverart, so no else branch here

		if (startIndex >= 0) {
			listComponent.getModel()?.setValue(songsListAdapter, startIndex, numRows, songsListAdapter.height)
		}
	}

	/**
	 * Shows the currently playing song.
	 */
	private fun showCurrentlyPlayingSong(showNeighbors: Boolean) {
		val oldPlayingIndex = songsList.indexOfFirst { it.queueId == currentSong?.queueId }
		currentSong = musicController.getMetadata()
		val playingIndex = songsList.indexOfFirst { it.queueId == currentSong?.queueId }

		// song actually playing is different than what the current song is, then update checkmark
		if (oldPlayingIndex != playingIndex) {
			// remove checkmark from old song
			if (oldPlayingIndex >= 0) {
				showList(oldPlayingIndex, 1)
			}
		}

		// add checkmark to new song
		if (showNeighbors) {
			showList(max(0, playingIndex - 5), 10)
		} else {
			showList(playingIndex, 1)
		}

		// move the selection if the previous song was selected
		if (oldPlayingIndex == selectedIndex) {
			setSelectionToCurrentSong()
		}
	}

	/**
	 * On click callback for when the user selects a queue list item.
	 */
	private fun onClick(index: Int) {
		val song = songsList.getOrNull(index)
		if (song?.queueId != null) {
			musicController.playQueue(song)
		}
	}
}