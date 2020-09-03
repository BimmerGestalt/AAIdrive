package me.hufman.androidautoidrive

import android.content.Context
import me.hufman.androidautoidrive.carapp.navigation.NavigationTriggerReceiver
import me.hufman.androidautoidrive.carapp.navigation.NavigationTriggerApp
import me.hufman.androidautoidrive.carapp.music.MusicApp
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.music.MusicAppDiscovery
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.idriveconnectionkit.android.security.SecurityAccess

class MusicService(val context: Context, val securityAccess: SecurityAccess) {
	var threadMusic: CarThread? = null
	var carappMusic: MusicApp? = null
	var navigationTriggerReceiver: NavigationTriggerReceiver? = null

	fun start(): Boolean {
		synchronized(this) {
			if (threadMusic == null) {
				threadMusic = CarThread("Music") {
					val handler = threadMusic?.handler ?: return@CarThread
					val musicAppDiscovery = MusicAppDiscovery(context, handler)
					val musicController = MusicController(context, handler)
					val carappMusic = MusicApp(securityAccess,
							CarAppAssetManager(context, "multimedia"),
							PhoneAppResourcesAndroid(context),
							GraphicsHelpersAndroid(),
							musicAppDiscovery,
							musicController,
							MusicAppMode(MutableAppSettings(context)))
					this.carappMusic = carappMusic

					// use this app's layout for navigation
					musicAppDiscovery.discoverApps()
					val navigationTrigger = NavigationTriggerApp(carappMusic.carApp)
					navigationTriggerReceiver = NavigationTriggerReceiver(navigationTrigger)
					navigationTriggerReceiver?.register(context, handler)
				}
				threadMusic?.start()
			}
		}
		return true
	}

	fun stop() {
		val handler = threadMusic?.handler
		handler?.post {
			navigationTriggerReceiver?.unregister(context)
			carappMusic?.musicController?.disconnectApp(pause=false)
			carappMusic?.musicAppDiscovery?.cancelDiscovery()
			carappMusic = null

			handler.looper?.quitSafely()
		}
		threadMusic = null
	}



}