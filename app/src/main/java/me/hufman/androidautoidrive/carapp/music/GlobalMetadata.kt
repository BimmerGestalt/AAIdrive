package me.hufman.androidautoidrive.carapp.music

import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.music.MusicAction
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.idriveconnectionkit.rhmi.*
import kotlin.math.max
import kotlin.math.min

class GlobalMetadata(app: RHMIApplication, var controller: MusicController) {
	val multimediaInfoEvent: RHMIEvent.MultimediaInfoEvent
	val statusbarEvent: RHMIEvent.StatusbarEvent
	val instrumentCluster: RHMIComponent.InstrumentCluster

	var displayedApp: MusicAppInfo? = null
	var displayedSong: MusicMetadata? = null
	var displayedQueue: List<MusicMetadata>? = null
	var icQueue: List<MusicMetadata> = ArrayList()

	init {
		multimediaInfoEvent = app.events.values.filterIsInstance<RHMIEvent.MultimediaInfoEvent>().first()
		statusbarEvent = app.events.values.filterIsInstance<RHMIEvent.StatusbarEvent>().first()
		instrumentCluster = app.components.values.filterIsInstance<RHMIComponent.InstrumentCluster>().first()
	}

	companion object {
		val QUEUE_SKIPPREVIOUS = MusicMetadata(mediaId = "__QUEUE_SKIPBACK__", title="< ${L.MUSIC_SKIP_PREVIOUS}")
		val QUEUE_SKIPNEXT = MusicMetadata(mediaId = "__QUEUE_SKIPNEXT__", title="${L.MUSIC_SKIP_NEXT} >")

		const val QUEUE_BACK_COUNT = 15 // how far back to allow scrolling
		const val QUEUE_NEXT_COUNT = 25 // how far forward to allow scrolling
	}

	fun initWidgets() {
		instrumentCluster.getSetTrackAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { onClick(it) }
	}

	fun redraw() {
		val app = if (!controller.getPlaybackPosition().playbackPaused) controller.currentAppInfo else null
		if (app != null && app != displayedApp) {
			showApp(app)
		}

		val song = controller.getMetadata()
		if (song != null && song != displayedSong) {
			showSong(song)
		}

		val queue = controller.getQueue()?.songs
		if (queue != displayedQueue || song != displayedSong) {
			val icQueue = prepareQueue(queue, song)
			showQueue(icQueue, song)
			this.icQueue = icQueue
		}

		displayedApp = app
		displayedSong = song
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
		trackModel?.value = UnicodeCleaner.clean(song.title ?: "")
		artistModel?.value = UnicodeCleaner.clean(song.artist ?: "")

		// show in the IC
		instrumentCluster.getTextModel()?.asRaDataModel()?.value = UnicodeCleaner.clean(song.title ?: "")

		// actually tell the car to load the data
		multimediaInfoEvent.triggerEvent()
	}

	/**
	 * Decorates a song queue with a Back/Next action around the current song
	 */
	fun prepareQueue(songQueue: List<MusicMetadata>?, currentSong: MusicMetadata?): List<MusicMetadata> {
		val queue = ArrayList<MusicMetadata>(songQueue?.size ?: 0 + 3)
		fun addPrevious(): Unit = if (controller.isSupportedAction(MusicAction.SKIP_TO_PREVIOUS)) { queue.add(QUEUE_SKIPPREVIOUS); Unit } else Unit
		fun addNext(): Unit = if (controller.isSupportedAction(MusicAction.SKIP_TO_NEXT)) { queue.add(QUEUE_SKIPNEXT); Unit } else Unit
		if (songQueue == null || songQueue.isEmpty()) {
			addPrevious()
			if (currentSong != null) { queue.add(currentSong) }
			addNext()
		} else {
			val index = songQueue.indexOfFirst { it.queueId == currentSong?.queueId }
			if (currentSong != null && index >= 0) {
				// add the previous/next actions around the current song
				// This allows for using the shuffle mode's back/next and also the queue selection
				queue.addAll(songQueue.subList(max(0, index - QUEUE_BACK_COUNT), index))
				addPrevious()
				queue.add(currentSong)
				addNext()
				if (index < songQueue.count()) {
					queue.addAll(songQueue.subList(index + 1, min(songQueue.count(), index + QUEUE_NEXT_COUNT)))
				}
			} else {
				queue.addAll(songQueue.subList(0, min(songQueue.count(), QUEUE_NEXT_COUNT)))
			}
		}
		return queue
	}

	private fun showQueue(queue: List<MusicMetadata>, currentSong: MusicMetadata?) {
		instrumentCluster.getUseCaseModel()?.asRaDataModel()?.value = "EntICPlaylist"

		val adapter = object: RHMIListAdapter<MusicMetadata>(7, queue) {
			override fun convertRow(index: Int, item: MusicMetadata): Array<Any> {
				val selected = item.queueId == currentSong?.queueId
				return arrayOf(
						index,  // index
						UnicodeCleaner.clean(item.title ?: ""),   // title
						UnicodeCleaner.clean(item.artist ?: ""),  // artist
						UnicodeCleaner.clean(item.album ?: ""),   // album
						-1,
						if (selected) 1 else 0, // checked
						true
				)
			}
		}

		instrumentCluster.getPlaylistModel()?.asRaListModel()?.setValue(adapter, 0, adapter.height, adapter.height)
	}

	private fun onClick(index: Int) {
		val song = icQueue.getOrNull(index)
		when {
			song == QUEUE_SKIPPREVIOUS -> controller.skipToPrevious()
			song == QUEUE_SKIPNEXT -> controller.skipToNext()
			song == controller.getMetadata() -> controller.seekTo(0)
			song?.queueId != null -> controller.playQueue(song)
		}
	}

}