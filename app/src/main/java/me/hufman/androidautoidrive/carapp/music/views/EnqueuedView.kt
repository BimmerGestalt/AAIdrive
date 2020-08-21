package me.hufman.androidautoidrive.carapp.music.views

import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.idriveconnectionkit.rhmi.*

class EnqueuedView(val state: RHMIState, val musicController: MusicController) {
	companion object {
		private const val IMAGEID_CHECKMARK = 149
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
	val songsListAdapter = object: RHMIListAdapter<MusicMetadata>(3, songsList) {
		override fun convertRow(index: Int, item: MusicMetadata): Array<Any> {
			val checkmark = if (item.queueId == currentSong?.queueId) BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, IMAGEID_CHECKMARK) else ""
			return arrayOf(checkmark,
						"",
						UnicodeCleaner.clean(item.title ?: ""))
		}
	}

	init {
		state as RHMIState.PlainState

		listComponent = state.componentsList.filterIsInstance<RHMIComponent.List>().first()

		songsEmptyList.addRow(arrayOf("", "", L.MUSIC_QUEUE_EMPTY))
	}

	fun initWidgets(playbackView: PlaybackView) {
		state.getTextModel()?.asRaDataModel()?.value = L.MUSIC_QUEUE_TITLE
		listComponent.setVisible(true)
		listComponent.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH, "57,50,*")
		listComponent.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = playbackView.state.id
		listComponent.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { onClick(it) }
	}

	fun show() {
		currentSong = musicController.getMetadata()
		songsList.clear()
		val songs = musicController.getQueue()
		if (songs?.isNotEmpty() == true) {
			listComponent.setEnabled(true)
			listComponent.setSelectable(true)
			songsList.addAll(songs)
			listComponent.getModel()?.setValue(songsListAdapter, 0, songsListAdapter.height, songsListAdapter.height)

			// set the selection to the current song
			val index = songs.indexOfFirst { it.queueId == currentSong?.queueId }
			if (index >= 0) {
				state.app.events.values.firstOrNull { it is RHMIEvent.FocusEvent }?.triggerEvent(
						mapOf(0.toByte() to listComponent.id, 41.toByte() to index)
				)
			}
		} else {
			listComponent.setEnabled(false)
			listComponent.setSelectable(false)
			listComponent.getModel()?.setValue(songsEmptyList, 0, songsEmptyList.height, songsEmptyList.height)
		}
	}

	fun onClick(index: Int) {
		val song = songsList.getOrNull(index)
		if (song?.queueId != null) {
			musicController.playQueue(song)
		}
	}
}