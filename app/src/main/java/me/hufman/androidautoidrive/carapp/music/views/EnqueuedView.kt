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
					state.componentsList.filterIsInstance<RHMIComponent.Image>().isEmpty() &&
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

	init {
		state as RHMIState.PlainState

		listComponent = state.componentsList.filterIsInstance<RHMIComponent.List>().first()

		songsEmptyList.addRow(arrayOf("", "", L.MUSIC_QUEUE_EMPTY))
	}

	fun initWidgets(playbackView: PlaybackView) {
		//problem of overlapped rows
//		listComponent.setProperty(RHMIProperty.PropertyId.WIDTH, 1000)
//		listComponent.setProperty(RHMIProperty.PropertyId.HEIGHT,100)

		//this is required for paging system
		listComponent.setProperty(RHMIProperty.PropertyId.VALID, false)

		listComponent.setVisible(true)
		listComponent.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH, "57,90,10,*")
		listComponent.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = playbackView.state.id
		listComponent.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { onClick(it) }
	}

	fun showList(startIndex: Int = 0, numRows: Int = 10) {
		listComponent.getModel()?.setValue(songsListAdapter, startIndex, numRows, songsListAdapter.height)
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
			currentSong = musicController.getMetadata()
			val playingIndex = songsList.indexOfFirst { it.queueId == currentSong?.queueId }

			//redraw for seek next
			showList(playingIndex-1)

			//redraw for seek previous
			showList(playingIndex+1)
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