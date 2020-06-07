package me.hufman.androidautoidrive

import android.content.Context
import me.hufman.androidautoidrive.carapp.CarConnectionBuilder
import me.hufman.androidautoidrive.carapp.music.MusicApp
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.music.MusicAppDiscovery
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.security.SecurityAccess

class MusicService(val context: Context, val securityAccess: SecurityAccess) {
	var threadMusic: CarThread? = null
	var carappMusic: MusicApp? = null

	fun start(): Boolean {
		synchronized(this) {
			if (threadMusic == null) {
				threadMusic = CarThread("Music") {
					val handler = threadMusic?.handler ?: return@CarThread
					val carConnectionBuilder = CarConnectionBuilder(IDriveConnectionListener(), securityAccess, CarAppAssetManager(context, "multimedia"), "me.hufman.androidautoidrive.music")
					var musicAppDiscovery = MusicAppDiscovery(context, handler)
					var musicController = MusicController(context, handler)
					carappMusic = MusicApp(carConnectionBuilder,
							PhoneAppResourcesAndroid(context),
							GraphicsHelpersAndroid(),
							musicAppDiscovery,
							musicController,
							MusicAppMode(MutableAppSettings(context)))
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