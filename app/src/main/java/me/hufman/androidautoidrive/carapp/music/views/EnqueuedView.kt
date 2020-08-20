package me.hufman.androidautoidrive.carapp.music.views

import android.graphics.Bitmap
import android.util.LruCache
import com.spotify.protocol.types.ImageUri
import de.bmw.idrive.BMWRemoting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.hufman.androidautoidrive.GraphicsHelpers
import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.carapp.notifications.views.DetailsView
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.idriveconnectionkit.rhmi.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.min

class EnqueuedView(val state: RHMIState, val musicController: MusicController, val graphicsHelpers: GraphicsHelpers): CoroutineScope {
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.IO

	companion object {
		private const val IMAGEID_CHECKMARK = 149

		//current default width only supports 22 chars before rolling over
		private const val MAX_LENGTH = 22

		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty() &&
					state.componentsList.filterIsInstance<RHMIComponent.Image>().isEmpty() &&
					state.componentsList.filterIsInstance<RHMIComponent.Separator>().isEmpty()
		}
	}

	//TODO: add playlist information (title and image)?
	//TODO: make list take up full size of screen and resize when right hand sidebar is pulled up

	val listComponent: RHMIComponent.List
	var currentSong: MusicMetadata? = null
	val songsList = ArrayList<MusicMetadata>()
	val songsEmptyList = RHMIModel.RaListModel.RHMIListConcrete(3)

	var coverArtCache = LruCache<String, ByteArray>(50)

	//var coverArtBySong = HashMap<String?,Bitmap?>()

	//test
	var coverArtBySong = HashMap<String?, ByteArray?>()
	//

//	val loadingSongList = RHMIModel.RaListModel.RHMIListConcrete(3).apply {
//		this.addRow(arrayOf("", "", L.MUSIC_BROWSE_LOADING))
//	}

	var songsListAdapter = object: RHMIListAdapter<MusicMetadata>(4, songsList) {
		override fun convertRow(index: Int, item: MusicMetadata): Array<Any> {
			val checkmark = if (item.queueId == currentSong?.queueId) BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, IMAGEID_CHECKMARK) else ""

			val coverArtImage = if (coverArtBySong[item.mediaId] != null) coverArtBySong[item.mediaId] as ByteArray else ""

			//default size is 144
			//val coverArt = if (coverArtImage != null) graphicsHelpers.compress(coverArtImage, 80, 80, quality = 40) else ""

			var title = item.title ?: ""
			if(title.length > MAX_LENGTH) {
				title = title.substring(0, 20) + "..."
			}

			var artist = item.artist ?: ""
			if(artist.length > MAX_LENGTH) {
				artist = artist.substring(0, 20) + "..."
			}

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
		state.getTextModel()?.asRaDataModel()?.value = L.MUSIC_QUEUE_TITLE

		//problem of overlapped rows
//		listComponent.setProperty(RHMIProperty.PropertyId.WIDTH, 1000)
//		listComponent.setProperty(RHMIProperty.PropertyId.HEIGHT,100)

		listComponent.setProperty(RHMIProperty.PropertyId.VALID, false)

		listComponent.setVisible(true)
		listComponent.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH, "57,90,10,*")
		listComponent.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = playbackView.state.id
		listComponent.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { onClick(it) }
	}

	fun showList(startIndex: Int = 0, numRows: Int = 10) {
		listComponent.getModel()?.setValue(songsListAdapter, startIndex, numRows, songsListAdapter.height)

		//trigger the cover art jobs
		val songs: List<MusicMetadata> = songsListAdapter.realData.subList(startIndex,startIndex+numRows)
		songs.forEachIndexed { index, song ->
			launch {
				val coverArt = musicController.getSongCoverArtAsync(ImageUri(song.coverArtUri)).await()!!
				coverArtBySong[song.mediaId] = graphicsHelpers.compress(coverArt,90,90, quality = 30)
			}
		}
	}

	fun show() {
		currentSong = musicController.getMetadata()
		songsList.clear()
		val songs = musicController.getQueue()
		if (songs?.isNotEmpty() == true) {
			listComponent.setEnabled(true)
			listComponent.setSelectable(true)
			songsList.addAll(songs)

			//coverArtBySong = musicController.getCoverArtByMediaId()!!

			listComponent.requestDataCallback = RequestDataCallback { startIndex, numRows ->
				showList(startIndex, numRows)
			}

			//only loads the first 10 entries
			//showList()
			
			//listComponent.getModel()?.setValue(songsListAdapter, 0, songsListAdapter.height, songsListAdapter.height)

			val selectedIndex = songsList.indexOfFirst { it.queueId == currentSong?.queueId }

			//load 3 entries before the selected
			showList(min(0,selectedIndex-3))

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

	//temporary disabled to reduce complexity
	fun redraw() {
//		if(currentSong?.mediaId != musicController.getMetadata()?.mediaId) {
//			currentSong = musicController.getMetadata()
//			listComponent.getModel()?.setValue(songsListAdapter, 0, songsListAdapter.height, songsListAdapter.height)
//		}
	}

	fun onClick(index: Int) {
		val song = songsList.getOrNull(index)
		if (song?.queueId != null) {
			musicController.playQueue(song)
		}
	}
}