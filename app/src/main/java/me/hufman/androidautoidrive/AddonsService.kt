package me.hufman.androidautoidrive

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import me.hufman.androidautoidrive.addons.AddonAppInfo
import me.hufman.androidautoidrive.addons.AddonDiscovery
import me.hufman.idriveconnectionkit.android.IDriveConnectionStatus
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import java.lang.Exception
import java.lang.IllegalArgumentException

class AddonsService(val context: Context, val iDriveConnectionStatus: IDriveConnectionStatus, val securityAccess: SecurityAccess) {
	companion object {
		const val TAG = "AddonsService"
	}

	val addonDiscovery = AddonDiscovery(context.packageManager)
	val boundDataAddons = HashMap<AddonAppInfo, AddonServiceConnection>()
	var running = false
	fun start(): Boolean {
		running = true
		synchronized(this) {
			addonDiscovery.discoverApps().forEach { appInfo ->
				if (appInfo.intentDataService != null) {
					if (!boundDataAddons.containsKey(appInfo)) {
						Log.i(TAG, "Binding data addon ${appInfo.name} ${appInfo.packageName}")
						val conn = AddonServiceConnection(appInfo)
						try {
							context.bindService(appInfo.intentDataService, conn, Context.BIND_AUTO_CREATE)
							boundDataAddons[appInfo] = conn
						} catch (e: SecurityException) {
							Log.w(TAG, "Error while binding service ${appInfo.name}: $e")
						}
					} else {
						Log.d(TAG, "Ensuring service for ${appInfo.name} is started")
						try {
							context.startService(appInfo.intentDataService)
						} catch (e: Exception) {
							Log.w(TAG, "Error while starting service ${appInfo.name}: $e")
						}
					}
				}
			}
		}
		return true
	}

	fun stop() {
		synchronized(this) {
			val bindings = ArrayList(boundDataAddons.keys)
			bindings.forEach { appInfo ->
				val binding = boundDataAddons[appInfo]
				if (appInfo.intentDataService != null && binding != null) {
					Log.i(TAG, "Unbinding data addon ${appInfo.name} ${appInfo.packageName}")
					try {
						context.unbindService(binding)
					} catch (e: IllegalArgumentException) {
						// complains for some reason, perhaps because the returned IBinder is null
					}
					try {
						context.stopService(appInfo.intentDataService)
					} catch (e: IllegalArgumentException) {
						Log.w(TAG, "Error while stopping service ${appInfo.name}: $e")
					}
				}
				boundDataAddons.remove(appInfo)
			}
		}
	}

	/** Receive updates about the connection status */
	class AddonServiceConnection(val appInfo: AddonAppInfo): ServiceConnection {
		var connected = false
			private set

		override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
			Log.i(TAG, "Successful connection to ${appInfo.name}")
			connected = true
		}
		override fun onNullBinding(name: ComponentName?) {
			Log.i(TAG, "Successful null connection to ${appInfo.name}")
			connected = true
		}
		override fun onServiceDisconnected(name: ComponentName?) {
			Log.i(TAG, "Disconnected from ${appInfo.name}")
			connected = false
		}
	}
}