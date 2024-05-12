package me.hufman.androidautoidrive.carapp.music

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.CarAppService
import me.hufman.androidautoidrive.carapp.navigation.NavigationTriggerApp
import me.hufman.androidautoidrive.carapp.navigation.NavigationTriggerReceiver
import me.hufman.androidautoidrive.connections.BtStatus
import me.hufman.androidautoidrive.music.MusicAppDiscovery
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.utils.GraphicsHelpersAndroid
import java.io.IOException

class MusicAppService: CarAppService() {
	var carappMusic: MusicApp? = null
	var navigationTriggerReceiver: NavigationTriggerReceiver? = null

	// watch for bluetooth audio connection changes
	private val btConnectionCallback by lazy {
		BtStatus(applicationContext) {
			// defer a bit, in case the Bluetooth Audio Sink takes a bit to create
			handler?.postDelayed({
				setMaxBluetoothVolume()
			}, 1000)
			// check again in a few seconds, in case of really busy/slow phones
			handler?.postDelayed({
				setMaxBluetoothVolume()
			}, 3000)
		}
	}

	override fun shouldStartApp(): Boolean {
		return true
	}

	override fun onCarStart() {
		// load the emoji dictionary, used by music app
		UnicodeCleaner.init(applicationContext)

		// make sure bluetooth volume is set to max
		btConnectionCallback.register()
		btConnectionCallback.callback.invoke()

		val handler = handler ?: return
		val musicAppDiscovery = MusicAppDiscovery(applicationContext, handler)
		val musicController = MusicController(applicationContext, handler)
		val musicAppMode = MusicAppMode.build(carInformation.capabilities, applicationContext)
		var carappMusic: MusicApp? = null
		if (musicAppMode.supportsId5Playback()) {
			try {
				carappMusic = MusicApp(iDriveConnectionStatus, securityAccess,
						CarAppAssetResources(applicationContext, "spotify"),
						MusicImageIDsSpotify,
						PhoneAppResourcesAndroid(applicationContext),
						GraphicsHelpersAndroid(),
						musicAppDiscovery,
						musicController,
						musicAppMode)
			} catch (e: IOException) {
				Log.w(TAG, "Failed to initialize ID5 music, falling back to ID4", e)
			}
		}
		if (carappMusic == null) {
			carappMusic = MusicApp(iDriveConnectionStatus, securityAccess,
					CarAppAssetResources(applicationContext, "multimedia"),
					MusicImageIDsMultimedia,
					PhoneAppResourcesAndroid(applicationContext),
					GraphicsHelpersAndroid(),
					musicAppDiscovery,
					musicController,
					musicAppMode)
		}
		this.carappMusic = carappMusic

		// use this app's layout for navigation
		musicAppDiscovery.discoverApps()
		val navigationTrigger = NavigationTriggerApp(carappMusic.carApp)
		navigationTriggerReceiver = NavigationTriggerReceiver(navigationTrigger)
		navigationTriggerReceiver?.register(applicationContext, handler)
	}

	override fun onCarStop() {
		// disconnect from music apps when disconnecting from the car
		try {
			btConnectionCallback.unregister()
			navigationTriggerReceiver?.unregister(applicationContext)
			navigationTriggerReceiver = null
			if (carappMusic?.avContext?.currentContext == true) {
				carappMusic?.musicController?.pauseSync()
			}
			carappMusic?.musicController?.disconnectApp(pause = false)
			carappMusic?.musicAppDiscovery?.cancelDiscovery()
		} catch (e: Exception) {
			Log.w(TAG, "Encountered an exception while shutting down", e)
		}

		carappMusic = null
	}

	private fun setMaxBluetoothVolume() {
		// make sure car (a device named BMW or MINI) is connected over A2DP
		if (!btConnectionCallback.isA2dpConnected) {
			return
		}

		// make sure the A2DP audio device is present
		// there's a slight delay between the A2DP connection and the audio sink creation
		val audioManager = applicationContext.getSystemService(AudioManager::class.java)
		val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
		val btDevices = audioDevices.filter {
			it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
		}
		if (btDevices.isNotEmpty()) {
			try {
				val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
				audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)

				// once we set the volume, unregister the BtStatus listener from future updates
				btConnectionCallback.unregister()
			} catch (e: SecurityException) {
				Log.w(TAG, "Unable to set Bluetooth volume", e)
			}
		}
	}
}