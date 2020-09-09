package me.hufman.androidautoidrive.carapp.music.views

import de.bmw.idrive.BMWRemoting
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.GraphicsHelpers
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.music.QueueMetadata
import me.hufman.idriveconnectionkit.rhmi.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.max

class EnqueuedView(val state: RHMIState, val musicController: MusicController, val graphicsHelpers: GraphicsHelpers): CoroutineScope {
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.IO

	companion object {
		private const val IMAGEID_CHECKMARK = 149

		//current default row width only supports 22 chars before rolling over
		private const val ROW_LINE_MAX_LENGTH = 22

		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty() &&
					state.componentsList.filterIsInstance<RHMIComponent.Image>().isNotEmpty() &&
					state.componentsList.filterIsInstance<RHMIComponent.Separator>().isEmpty()
		}
	}

	val listComponent: RHMIComponent.List
	var currentSong: MusicMetadata? = null
	val songsList = ArrayList<MusicMetadata>()
	val songsEmptyList = RHMIModel.RaListModel.RHMIListConcrete(3)
	var queueMetadata: QueueMetadata? = null
	var currentlyVisibleRows: List<MusicMetadata> = emptyList()
	var currentVisibleRowsMusicMetadata: ArrayList<MusicMetadata> = ArrayList()
	var currentIndex: Int = 0

	var songsListAdapter = object: RHMIListAdapter<MusicMetadata>(4, songsList) {
		override fun convertRow(index: Int, item: MusicMetadata): Array<Any> {
			val checkmark = if (item.queueId == currentSong?.queueId) BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, IMAGEID_CHECKMARK) else ""

			val coverArtImage = if (item.coverArt != null) graphicsHelpers.compress(item.coverArt!!, 90, 90, quality = 30) else ""

			var title = item.title ?: ""
			if(title.length > ROW_LINE_MAX_LENGTH) {
				title = title.substring(0, 20) + "..."
			}

			val artist = item.artist ?: ""

			val songMetaDataText = "${title}\n${artist}"

			return arrayOf(
					checkmark,
					coverArtImage,
					"",
					songMetaDataText
			)
		}
	}

	val imageComponent: RHMIComponent.Image
	val labelComponent0: RHMIComponent.Label
	val labelComponent1: RHMIComponent.Label
	val labelComponent2: RHMIComponent.Label

	init {
		state as RHMIState.PlainState

		listComponent = state.componentsList.filterIsInstance<RHMIComponent.List>().first()

		imageComponent = state.componentsList.filterIsInstance<RHMIComponent.Image>().first()

		//all 3 labels next to image at top of list don't scroll and are static
		//TODO: IDEA - can have it so the playlist information is on the right side of the screen in the blank space and is fixed
		//      problem is that this isn't independent of screen sizes and not flexible in the cases of operations such as pulling up
		//      the sidebar and running splitscreen
		//      IDEA - figure out how to move the Y position of the listComponent down so the image + text is above the list. The position component
		//      of the listView is having an effect where all the children are fixed at the specified position and is not applying to the container itself
		labelComponent0 = state.componentsList.filterIsInstance<RHMIComponent.Label>()[0]
		labelComponent1 = state.componentsList.filterIsInstance<RHMIComponent.Label>()[1]
		labelComponent2 = state.componentsList.filterIsInstance<RHMIComponent.Label>()[2]

		songsEmptyList.addRow(arrayOf("", "", L.MUSIC_QUEUE_EMPTY))
	}

	fun initWidgets(playbackView: PlaybackView) {
		//this is required for pagination system
		listComponent.setProperty(RHMIProperty.PropertyId.VALID, false)

		listComponent.setVisible(true)
		listComponent.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH, "57,90,10,*")
		listComponent.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = playbackView.state.id
		listComponent.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { onClick(it) }
		listComponent.getSelectAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index -> onSelectAction(index) }
	}

//	var prevIndex = 0
//	fun onSelectAction(index: Int) {
//		//WIP hacky solution to hiding text
//		var scrollingUp = false
//		if(index < prevIndex) {
//			scrollingUp = true
//		}
//		if(index > 1 && !scrollingUp) {
//			labelComponent0.setVisible(false)
//			labelComponent1.setVisible(false)
//		}
//		else if(index == 0) {
//			labelComponent0.setVisible(true)
//			labelComponent1.setVisible(true)
//		}
//	}

	fun onSelectAction(index: Int) {
		currentIndex = index
	}

	fun showList(startIndex: Int = 0, numRows: Int = 10) {
		if(startIndex >= 0) {
			listComponent.getModel()?.setValue(songsListAdapter, startIndex, numRows, songsListAdapter.height)
		}
	}

	fun show() {
		currentSong = musicController.getMetadata()
		queueMetadata = musicController.getQueue()
		songsList.clear()

		val songs = queueMetadata?.songs
		if (songs?.isNotEmpty() == true) {
			listComponent.setEnabled(true)
			listComponent.setSelectable(true)
			songsList.addAll(songs)

			listComponent.requestDataCallback = RequestDataCallback { startIndex, numRows ->
				showList(startIndex, numRows)

				val endIndex = if (startIndex+numRows >= songsList.size) songsList.size-1 else startIndex+numRows
				currentlyVisibleRows = songsListAdapter.realData.subList(startIndex,endIndex+1)

				currentVisibleRowsMusicMetadata.clear()
				currentlyVisibleRows.forEach { musicMetadata ->
					currentVisibleRowsMusicMetadata.add(MusicMetadata.copy(musicMetadata))
				}
			}

			val selectedIndex = songsList.indexOfFirst { it.queueId == currentSong?.queueId }
			showList(selectedIndex)
			setSelectionToCurrentSong(selectedIndex)
		} else {
			listComponent.setEnabled(false)
			listComponent.setSelectable(false)
			listComponent.getModel()?.setValue(songsEmptyList, 0, songsEmptyList.height, songsEmptyList.height)
		}
		if(queueMetadata?.coverArt != null) {
			imageComponent.getModel()?.asRaImageModel()?.value = graphicsHelpers.compress(musicController.getQueue()?.coverArt!!, 200, 200, quality = 60)
//			labelComponent0.getModel()?.asRaDataModel()?.value = queueMetadata.title ?: ""
//			labelComponent1.getModel()?.asRaDataModel()?.value = queueMetadata.subtitle ?: ""
//			labelComponent2.getModel()?.asRaDataModel()?.value = "LINE 3"
		}
		else {
			imageComponent.setVisible(false)
		}

		state.getTextModel()?.asRaDataModel()?.value = "Now Playing - ${queueMetadata?.title}"
	}

	/**
	 * Sets the selection to the current song
	 */
	fun setSelectionToCurrentSong(index: Int) {
		if (index >= 0) {
			state.app.events.values.firstOrNull { it is RHMIEvent.FocusEvent }?.triggerEvent(
					mapOf(0.toByte() to listComponent.id, 41.toByte() to index)
			)
		}
	}

	fun redraw() {
		//need a full redraw as the queue is either different or has been modified
		if(musicController.getQueue()?.title != queueMetadata?.title || musicController.getQueue()?.songs?.size != queueMetadata?.songs?.size) {
			show()
			return
		}

		//song actually playing is different than what the current song is then update checkmark
		if(currentSong?.mediaId != musicController.getMetadata()?.mediaId) {
			val oldPlayingIndex = songsList.indexOfFirst { it.queueId == currentSong?.queueId }
			currentSong = musicController.getMetadata()
			val playingIndex = songsList.indexOfFirst { it.queueId == currentSong?.queueId }

			// remove checkmark from old song
			showList(oldPlayingIndex, 1)

			//add checkmark to new song
			showList(playingIndex, 1)
		}

		//redraw currently visible rows if one of them has a cover art that was retrieved
		for ((index, metadata) in currentVisibleRowsMusicMetadata.withIndex()) {
			if(metadata != currentlyVisibleRows[index]) {

				//can only see roughly 5 rows (2 before selected index)
				showList(max(0,currentIndex-3),4)
				break
			}
		}
	}

	fun onClick(index: Int) {
		val song = songsList.getOrNull(index)
		if (song?.queueId != null) {
			musicController.playQueue(song)
		}
	}
}