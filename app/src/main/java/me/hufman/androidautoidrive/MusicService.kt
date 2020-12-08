package me.hufman.androidautoidrive

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.carapp.navigation.NavigationTriggerReceiver
import me.hufman.androidautoidrive.carapp.navigation.NavigationTriggerApp
import me.hufman.androidautoidrive.carapp.music.MusicApp
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.carapp.music.MusicImageIDsMultimedia
import me.hufman.androidautoidrive.carapp.music.MusicImageIDsSpotify
import me.hufman.androidautoidrive.connections.BtStatus
import me.hufman.androidautoidrive.music.MusicAppDiscovery
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.utils.GraphicsHelpersAndroid
import me.hufman.idriveconnectionkit.android.IDriveConnectionStatus
import me.hufman.idriveconnectionkit.android.security.SecurityAccess

class MusicService(val context: Context, val iDriveConnectionStatus: IDriveConnectionStatus, val securityAccess: SecurityAccess, val musicAppMode: MusicAppMode) {
	var threadMusic: CarThread? = null
	var carappMusic: MusicApp? = null
	var navigationTriggerReceiver: NavigationTriggerReceiver? = null

	// watch for bluetooth audio connection changes
	private val btConnectionCallback = BtStatus(context) {
		// defer a bit, in case the Bluetooth Audio Sink takes a bit to create
		threadMusic?.handler?.postDelayed({
			setMaxBluetoothVolume()
		}, 1000)
		// check again in a few seconds, in case of really busy/slow phones
		threadMusic?.handler?.postDelayed({
			setMaxBluetoothVolume()
		}, 3000)
	}

	fun start(): Boolean {

		synchronized(this) {
			if (threadMusic?.isAlive != true) {
				threadMusic = CarThread("Music") {
					// make sure bluetooth volume is set to max
					btConnectionCallback.register()
					btConnectionCallback.callback.invoke()

					val handler = threadMusic?.handler ?: return@CarThread
					val musicAppDiscovery = MusicAppDiscovery(context, handler)
					val musicController = MusicController(context, handler)
					var carappMusic: MusicApp? = null
					if (musicAppMode.shouldId5Playback()) {
						try {
							carappMusic = MusicApp(iDriveConnectionStatus, securityAccess,
									CarAppAssetManager(context, "spotify"),
									MusicImageIDsSpotify,
									PhoneAppResourcesAndroid(context),
									GraphicsHelpersAndroid(),
									musicAppDiscovery,
									musicController,
									musicAppMode)
						} catch (e: BMWRemoting.ServiceException) {
							Log.w(TAG, "Failed to initialize ID5 music, falling back to ID4", e)
						}
					}
					if (carappMusic == null) {
						carappMusic = MusicApp(iDriveConnectionStatus, securityAccess,
								CarAppAssetManager(context, "multimedia"),
								MusicImageIDsMultimedia,
								PhoneAppResourcesAndroid(context),
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
					navigationTriggerReceiver?.register(context, handler)
				}
				threadMusic?.start()
			}
		}
		return true
	}

	private fun setMaxBluetoothVolume() {
		// make sure car (a device named BMW or MINI) is connected over A2DP
		if (!btConnectionCallback.isA2dpConnected) {
			return
		}

		// make sure the A2DP audio device is present
		// there's a slight delay between the A2DP connection and the audio sink creation
		val audioManager = context.getSystemService(AudioManager::class.java)
		val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
		val btDevices = audioDevices.filter {
			it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
		}
		if (btDevices.isNotEmpty()) {
			try {
				val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
				audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
			} catch (e: SecurityException) {
				Log.w(TAG, "Unable to set Bluetooth volume", e)
			}
		}
	}

	fun stop() {
		btConnectionCallback.unregister()

		threadMusic?.post {
			navigationTriggerReceiver?.unregister(context)
			carappMusic?.musicController?.disconnectApp(pause=false)
			carappMusic?.musicAppDiscovery?.cancelDiscovery()
			carappMusic = null
		}
		threadMusic?.quitSafely()
		threadMusic = null
	}



}