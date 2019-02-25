package me.hufman.androidautoidrive.carapp.music

import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.idriveconnectionkit.rhmi.*

class GlobalMetadata(app: RHMIApplication, var controller: MusicController) {
	val multimediaInfoEvent: RHMIEvent.MultimediaInfoEvent
	val statusbarEvent: RHMIEvent.StatusbarEvent
	val instrumentCluster: RHMIComponent.InstrumentCluster

	var displayedApp: MusicAppInfo? = null
	var displayedSong: MusicMetadata? = null
	var displayedQueue: List<MusicMetadata>? = null

	init {
		multimediaInfoEvent = app.events.values.filterIsInstance<RHMIEvent.MultimediaInfoEvent>().first()
		statusbarEvent = app.events.values.filterIsInstance<RHMIEvent.StatusbarEvent>().first()
		instrumentCluster = app.components.values.filterIsInstance<RHMIComponent.InstrumentCluster>().first()
	}

	fun initWidgets() {
		instrumentCluster.getSetTrackAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { onClick(it) }
	}

	fun redraw() {
		val app = if (!controller.getPlaybackPosition().playbackPaused) controller.currentApp?.musicAppInfo else null
		if (app != null && app != displayedApp) {
			showApp(app)
		}
		displayedApp = app

		val song = controller.getMetadata()
		if (song != null && song != displayedSong) {
			showSong(song)
		}
		displayedSong = song

		val queue = controller.getQueue()
		if (queue != displayedQueue) {
			showQueue(queue)
		}
		displayedQueue = queue
	}

	private fun showApp(app: MusicAppInfo) {
		// set the name of the app
		statusbarEvent.getTextModel()?.asRaDataModel()?.value = app.name
		statusbarEvent.triggerEvent()
	}

	private fun showSong(song: MusicMetadata) {
		// show in the sidebar
		val trackModel = multimediaInfoEvent.getTextModel1()?.asRaDataModel()
		val artistModel = multimediaInfoEvent.getTextModel2()?.asRaDataModel()
		trackModel?.value = song.title ?: ""
		artistModel?.value = song.artist ?: ""

		// show in the IC
		instrumentCluster.getTextModel()?.asRaDataModel()?.value = song.title ?: ""

		// actually tell the car to load the data
		multimediaInfoEvent.triggerEvent()
	}

	/** TODO Verify this api
	 * my test Mini doesn't support it when Spotify tries it
	 * */
	private fun showQueue(queue: List<MusicMetadata>?) {
		if (queue == null || queue.isEmpty()) {
			instrumentCluster.getUseCaseModel()?.asRaDataModel()?.value = ""
		} else {
			instrumentCluster.getUseCaseModel()?.asRaDataModel()?.value = "EntICPlaylist"

			val adapter = object: RHMIListAdapter<MusicMetadata>(7, queue) {
				override fun convertRow(index: Int, item: MusicMetadata): Array<Any> {
					val selected = item.queueId == displayedSong?.queueId
					return arrayOf(
							index,  // index
							item.title ?: "",   // title
							item.artist ?: "",  // artist
							item.album ?: "",   // album
							-1,
							if (selected) 1 else 0, // checked
							true
					)
				}
			}

			instrumentCluster.getPlaylistModel()?.asRaListModel()?.setValue(adapter, 0, adapter.height, adapter.height)
		}
	}

	private fun onClick(index: Int) {
		val song = displayedQueue?.getOrNull(index)
		if (song?.queueId != null) {
			controller.playQueue(song)
		}
	}

}