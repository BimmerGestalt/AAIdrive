package me.hufman.androidautoidrive.carapp.music.views

import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.GraphicsHelpers
import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.carapp.notifications.views.DetailsView
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.idriveconnectionkit.rhmi.*
import kotlin.math.min

class EnqueuedView(val state: RHMIState, val musicController: MusicController, val graphicsHelpers: GraphicsHelpers) {
	companion object {
		private const val IMAGEID_CHECKMARK = 149

		//current default width only supports 22 chars before rolling over
		private const val MAX_LENGTH = 20

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
				title = title.substring(0, MAX_LENGTH) + "..."
			}

			var artist = item.artist ?: ""
			if(artist.length > MAX_LENGTH) {
				artist = artist.substring(0, MAX_LENGTH) + "..."
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

		//cut type test
		//listComponent.setProperty(37, 4)

		listComponent.setVisible(true)
		listComponent.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH, "57,90,10,*")
		listComponent.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = playbackView.state.id
		listComponent.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { onClick(it) }
	}

	//todo: maybe show loading while songs are populated into the view? --> see browse view for how to do loading
	fun show() {
		currentSong = musicController.getMetadata()
		songsList.clear()
		val songs = musicController.getQueue()
		if (songs?.isNotEmpty() == true) {
			listComponent.setEnabled(true)
			listComponent.setSelectable(true)
			songsList.addAll(songs)

			coverArtBySong = musicController.getCoverArtByMediaId()!!

			listComponent.getModel()?.setValue(songsListAdapter, 0, songsListAdapter.height, songsListAdapter.height)

			setSelectionToCurrentSong()
		} else {
			listComponent.setEnabled(false)
			listComponent.setSelectable(false)
			listComponent.getModel()?.setValue(songsEmptyList, 0, songsEmptyList.height, songsEmptyList.height)
		}
	}

	// set the selection to the current song
	fun setSelectionToCurrentSong() {
		val index = songsList.indexOfFirst { it.queueId == currentSong?.queueId }
		if (index >= 0) {
			state.app.events.values.firstOrNull { it is RHMIEvent.FocusEvent }?.triggerEvent(
					mapOf(0.toByte() to listComponent.id, 41.toByte() to index)
			)
		}
	}

	//TODO: PROBLEM - refresh is still called even while view is not active anymore causing performance hit
	fun redraw() {
		if(currentSong?.mediaId != musicController.getMetadata()?.mediaId) {
			currentSong = musicController.getMetadata()
			//TODO: need to find way to selectively update values at index in model to get better performance
			//TODO: IDEA we only draw the first x amount of songs in the list and as the user scrolls the list is then updated
			//      when we need to update a specific index we only update the applicable ones and not redraw the whole list
			//      for examples of this dynamic loading see BrowsePageView#showList
			//
			//      to achieve this need to find the max number of songs that can fit into the view and load only those
			//      then when the user scrolls past that max the
			//
			//      load in maybe 50 songs or so, if the user scrolls past 25 or so then load the next 50 songs
			listComponent.getModel()?.setValue(songsListAdapter, 0, songsListAdapter.height, songsListAdapter.height)
		}
	}

	fun onClick(index: Int) {
		val song = songsList.getOrNull(index)
		if (song?.queueId != null) {
			musicController.playQueue(song)
		}
	}
}