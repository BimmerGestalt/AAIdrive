package me.hufman.androidautoidrive.carapp.music.views

import android.util.LruCache
import com.spotify.protocol.types.ImageUri
import de.bmw.idrive.BMWRemoting
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.GraphicsHelpers
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.music.QueueMetadata
import me.hufman.idriveconnectionkit.rhmi.*
import kotlin.coroutines.CoroutineContext

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
	val coverArtCache = LruCache<String, ByteArray>(50)
	var loaderJob: Job? = null

	var songsListAdapter = object: RHMIListAdapter<MusicMetadata>(4, songsList) {
		override fun convertRow(index: Int, item: MusicMetadata): Array<Any> {
			val checkmark = if (item.queueId == currentSong?.queueId) BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, IMAGEID_CHECKMARK) else ""
			//val coverArtImage = if(coverArtCache[item.mediaId] != null) coverArtCache[item.mediaId] else ""

			//test
			val coverArtRaw = item.coverArt
			var coverArtImage: Any = ""
			if(coverArtRaw != null) {
				coverArtImage = graphicsHelpers.compress(coverArtRaw, 90, 90, quality = 30)

			}
			//

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
	val labelcomponent2: RHMIComponent.Label

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
		labelcomponent2 = state.componentsList.filterIsInstance<RHMIComponent.Label>()[2]

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

	//test
	fun onSelectAction(index: Int) {
		startIndex = index
	}
	//

	fun showList(startIndex: Int = 0, numRows: Int = 10) {
		if(startIndex >= 0) {
			listComponent.getModel()?.setValue(songsListAdapter, startIndex, numRows, songsListAdapter.height)
		}
	}

	var currentlyVisibleRows: List<MusicMetadata> = emptyList()

	var queueMetadata: QueueMetadata? = null

	//test
	var currentVisibleRowsMusicMetadata: ArrayList<MusicMetadata> = ArrayList()
	var startIndex: Int = 0
	//

	fun show() {
		currentSong = musicController.getMetadata()
		songsList.clear()
		queueMetadata = musicController.getQueue()

		val songs = queueMetadata?.songs
		if (songs?.isNotEmpty() == true) {
			listComponent.setEnabled(true)
			listComponent.setSelectable(true)
			songsList.addAll(songs)

			listComponent.requestDataCallback = RequestDataCallback { startIndex, numRows ->
				showList(startIndex, numRows)

				val endIndex = if(startIndex+numRows >= songsList.size) songsList.size-1 else startIndex+numRows
				currentlyVisibleRows = songsListAdapter.realData.subList(startIndex,endIndex+1)

				//test
				currentVisibleRowsMusicMetadata.clear()
				currentlyVisibleRows.forEach { metadata ->
					currentVisibleRowsMusicMetadata.add(MusicMetadata(mediaId = metadata.mediaId, queueId = metadata.queueId, playable = metadata.playable, browseable = metadata.browseable,
							duration = metadata.duration, coverArt = metadata.coverArt, coverArtUri = metadata.coverArtUri, icon = metadata.icon, artist = metadata.artist, album = metadata.album,
							title = metadata.title, subtitle = metadata.subtitle, trackCount = metadata.trackCount, trackNumber = metadata.trackNumber))
				}

				//

//				loaderJob = launch {
//					currentlyVisibleRows.forEachIndexed { index, song ->
//						if(coverArtCache.get(song.mediaId) == null) {
//							launch {
//								val coverArtRaw = musicController.getSongCoverArtAsync(ImageUri(song.coverArtUri)).await()
//								if (coverArtRaw != null) {
//									val coverArtImage = graphicsHelpers.compress(coverArtRaw, 90, 90, quality = 30)
//
//									synchronized(coverArtCache) {
//										coverArtCache.put(song.mediaId, coverArtImage)
//									}
//
//									//only redraw the visible rows
//									if(currentlyVisibleRows.contains(song)) {
//										showList(startIndex+index,1)
//									}
//								}
//							}
//						}
//					}
//				}
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
			//labelcomponent2.getModel()?.asRaDataModel()?.value = "LINE 3"
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

		//test
		//TODO: cover art doesn't show up for rows 0 - 4 in every playlist
		for ((index, value) in currentVisibleRowsMusicMetadata.withIndex()) {
			if(value != currentlyVisibleRows[index]) {
				showList(startIndex-3,10)
				break
			}
		}
		//
	}

	fun onClick(index: Int) {
		val song = songsList.getOrNull(index)
		if (song?.queueId != null) {
			musicController.playQueue(song)
		}
	}

	fun killLoaderJobs() {
		loaderJob?.cancelChildren()
		loaderJob?.cancel()
	}
}