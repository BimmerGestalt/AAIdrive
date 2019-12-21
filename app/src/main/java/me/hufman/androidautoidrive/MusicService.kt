package me.hufman.androidautoidrive

import android.content.Context
import me.hufman.androidautoidrive.carapp.music.MusicApp
import me.hufman.androidautoidrive.music.MusicAppDiscovery
import me.hufman.androidautoidrive.music.MusicController

class MusicService(val context: Context) {
	var threadMusic: CarThread? = null
	var carappMusic: MusicApp? = null

	fun start(): Boolean {
		synchronized(this) {
			if (threadMusic == null) {
				threadMusic = CarThread("Music") {
					val handler = threadMusic?.handler ?: return@CarThread
					var musicAppDiscovery = MusicAppDiscovery(context, handler)
					var musicController = MusicController(context, handler)
					carappMusic = MusicApp(CarAppAssetManager(context, "multimedia"),
							PhoneAppResourcesAndroid(context),
							GraphicsHelpersAndroid(),
							musicAppDiscovery,
							musicController)
					musicAppDiscovery.discoverApps()
				}
				threadMusic?.start()
			}
		}
		return true
	}

	fun stop() {
		val handler = threadMusic?.handler
		handler?.post {
			carappMusic?.musicController?.disconnectApp(pause=false)
			carappMusic?.musicAppDiscovery?.cancelDiscovery()
			carappMusic = null

			handler.looper?.quitSafely()
		}
		threadMusic = null
	}



}