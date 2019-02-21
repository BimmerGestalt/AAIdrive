package me.hufman.androidautoidrive.carapp.music

import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.idriveconnectionkit.rhmi.RHMIApplication
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import me.hufman.idriveconnectionkit.rhmi.RHMIEvent

class GlobalMetadata(app: RHMIApplication, var controller: MusicController) {
	val multimediaInfoEvent: RHMIEvent.MultimediaInfoEvent
	val statusbarEvent: RHMIEvent.StatusbarEvent
	val instrumentCluster: RHMIComponent.InstrumentCluster

	var displayedApp: MusicAppInfo? = null
	var displayedSong: MusicMetadata? = null

	init {
		multimediaInfoEvent = app.events.values.filterIsInstance<RHMIEvent.MultimediaInfoEvent>().first()
		statusbarEvent = app.events.values.filterIsInstance<RHMIEvent.StatusbarEvent>().first()
		instrumentCluster = app.components.values.filterIsInstance<RHMIComponent.InstrumentCluster>().first()
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

}