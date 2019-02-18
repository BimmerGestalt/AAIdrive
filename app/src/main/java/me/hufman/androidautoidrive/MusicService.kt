package me.hufman.androidautoidrive

import android.content.Context
import android.os.Handler
import android.os.Looper
import me.hufman.androidautoidrive.carapp.music.MusicApp
import me.hufman.androidautoidrive.music.MusicAppDiscovery
import me.hufman.androidautoidrive.music.MusicController

class MusicService(val context: Context) {
	val handler = Handler(Looper.myLooper())
	var threadMusic: CarThread? = null
	var carappMusic: MusicApp? = null
	var musicAppDiscovery = MusicAppDiscovery(context, handler)
	var musicController = MusicController(context, handler)

	fun start(): Boolean {
		synchronized(this) {
			if (threadMusic == null) {
				threadMusic = CarThread("Music") {
					carappMusic = MusicApp(CarAppAssetManager(context, "multimedia"),
							PhoneAppResourcesAndroid(context),
							musicAppDiscovery,
							musicController)
				}
				threadMusic?.start()
				musicAppDiscovery.discoverApps()
				scheduleRedrawProgress()
			}
		}
		return true
	}

	fun stop() {
		threadMusic?.handler?.looper?.quit()
		threadMusic = null
		carappMusic?.musicController?.disconnectApp()
		carappMusic?.musicAppDiscovery?.cancelDiscovery()
		carappMusic = null
	}


	val redrawProgressTask = Runnable {
		redrawProgress()
	}
	fun redrawProgress() {
		handler.removeCallbacks(redrawProgressTask)

		if (carappMusic?.playbackViewVisible == true) {
			carappMusic?.redraw()
		}
		scheduleRedrawProgress()
	}

	fun scheduleRedrawProgress() {
		val position = musicController.getPlaybackPosition()
		if (position.playbackPaused) {
			handler.postDelayed(redrawProgressTask, 1000)
		} else {
			// the time until the next second interval
			val delay = 1000 - position.getPosition() % 1000
			handler.postDelayed(redrawProgressTask, delay)
		}
	}

}