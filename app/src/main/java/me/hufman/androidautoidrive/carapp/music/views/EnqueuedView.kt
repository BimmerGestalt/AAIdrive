package me.hufman.androidautoidrive.carapp.music.views

import android.util.LruCache
import com.spotify.protocol.types.ImageUri
import de.bmw.idrive.BMWRemoting
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.GraphicsHelpers
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
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
			val coverArtImage = if(coverArtCache[item.mediaId] != null) coverArtCache[item.mediaId] else ""

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

		state.setProperty(24, 3)

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
	}

	fun showList(startIndex: Int = 0, numRows: Int = 10) {
		if(startIndex >= 0) {
			listComponent.getModel()?.setValue(songsListAdapter, startIndex, numRows, songsListAdapter.height)
		}
	}

	fun show() {
		currentSong = musicController.getMetadata()
		songsList.clear()
		val queueMetadata = musicController.getQueue()

		state.getTextModel()?.asRaDataModel()?.value = "Now Playing - ${queueMetadata?.title}"

		val songs = queueMetadata?.songs
		if (songs?.isNotEmpty() == true) {
			listComponent.setEnabled(true)
			listComponent.setSelectable(true)
			songsList.addAll(songs)

			listComponent.requestDataCallback = RequestDataCallback { startIndex, numRows ->
				showList(startIndex, numRows)

				//TODO: need new implementation - thrashing the list (quick scrolling all over the place) causes way too many threads to be spawned and they are not all being canceled which lags
				//  the system hard and hangs loading
				//  -
				//  IDEA: use a stack and pop off first 20 jobs or so (semaphore limited to not thread bomb the system)
				//  when we get a new requestDataCallback we can purge the stack (or when the size is > x before the new requestDataCallback threads are placed on
				//  purge the stack)
				//  - worry with the above is the overhead
				//  IDEA: use a dequeue that maintains a max size and operates from the same end similar to a stack (LIFO), when size begins to grow past n elements then
				//  remove older elements from the dequeue. A semaphore will still need to be used to limit the max number of threads
				killLoaderJobs()
				loaderJob = launch {
					val endIndex = if(startIndex+numRows >= songsList.size) songsList.size-1 else startIndex+numRows
					val requestDataSongs: List<MusicMetadata> = songsListAdapter.realData.subList(startIndex,endIndex)
					requestDataSongs.forEachIndexed { index, song ->
						if(coverArtCache.get(song.mediaId) == null) {
							launch {
								val coverArtRaw = musicController.getSongCoverArtAsync(ImageUri(song.coverArtUri)).await()
								if (coverArtRaw != null) {
									val coverArtImage = graphicsHelpers.compress(coverArtRaw, 90, 90, quality = 30)
									synchronized(coverArtCache) {
										coverArtCache.put(song.mediaId, coverArtImage)
									}
									showList(startIndex+index,1)
								}
							}
						}
					}
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
			labelComponent0.getModel()?.asRaDataModel()?.value = queueMetadata.title ?: ""
			//labelComponent1.getModel()?.asRaDataModel()?.value = "LINE 2"
			//labelcomponent2.getModel()?.asRaDataModel()?.value = "LINE 3"
		}
		else {
			imageComponent.setVisible(false)
		}
	}

	// set the selection to the current song
	fun setSelectionToCurrentSong(index: Int) {
		if (index >= 0) {
			state.app.events.values.firstOrNull { it is RHMIEvent.FocusEvent }?.triggerEvent(
					mapOf(0.toByte() to listComponent.id, 41.toByte() to index)
			)
		}
	}

	fun redraw() {
		if(currentSong?.mediaId != musicController.getMetadata()?.mediaId) {
			val oldPlayingIndex = songsList.indexOfFirst { it.queueId == currentSong?.queueId }
			currentSong = musicController.getMetadata()
			val playingIndex = songsList.indexOfFirst { it.queueId == currentSong?.queueId }

			// remove checkmark from old song
			showList(oldPlayingIndex, 1)

			//add checkmark to new song
			showList(playingIndex, 1)
		}
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