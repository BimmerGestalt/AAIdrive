package me.hufman.androidautoidrive.addons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess

class AddonsService(val context: Context, val iDriveConnectionStatus: IDriveConnectionStatus, val securityAccess: SecurityAccess) {
	companion object {
		const val TAG = "AddonsService"
	}

	val addonDiscovery = AddonDiscovery(context.packageManager)
	val boundConnectionAddons = HashMap<AddonAppInfo, AddonServiceConnection>()
	val boundDataAddons = HashMap<AddonAppInfo, AddonServiceConnection>()
	var running = false
	fun start(): Boolean {
		running = true
		synchronized(this) {
			addonDiscovery.discoverApps().forEach { appInfo ->
				val bindFlags = if (Build.VERSION.SDK_INT >= 34) {
					Context.BIND_AUTO_CREATE or Context.BIND_ALLOW_ACTIVITY_STARTS
				} else { Context.BIND_AUTO_CREATE }
				val connectionServiceIntent = appInfo.intentConnectionService
				if (connectionServiceIntent != null) {
					val connectionIntent = prepareCarConnectionIntent(connectionServiceIntent)
					if (!boundConnectionAddons.containsKey(appInfo)) {
						Log.i(TAG, "Binding connection addon ${appInfo.name} ${appInfo.packageName}")
						val conn = AddonServiceConnection(appInfo)
						try {
							context.bindService(connectionIntent, conn, bindFlags)
							boundConnectionAddons[appInfo] = conn
						} catch (e: SecurityException) {
							Log.w(TAG, "Error while binding connection service ${appInfo.name}: $e")
						}
					} else {
						Log.d(TAG, "Ensuring connection service for ${appInfo.name} is started")
						try {
							context.startService(connectionIntent)
						} catch (e: Exception) {
							Log.w(TAG, "Error while starting connection service ${appInfo.name}: $e")
						}
					}
				}
				val dataServiceIntent = appInfo.intentDataService
				if (dataServiceIntent != null) {
					if (!boundDataAddons.containsKey(appInfo)) {
						Log.i(TAG, "Binding data addon ${appInfo.name} ${appInfo.packageName}")
						val conn = AddonServiceConnection(appInfo)
						try {
							context.bindService(dataServiceIntent, conn, bindFlags)
							boundDataAddons[appInfo] = conn
						} catch (e: SecurityException) {
							Log.w(TAG, "Error while binding data service ${appInfo.name}: $e")
						}
					} else {
						Log.d(TAG, "Ensuring data service for ${appInfo.name} is started")
						try {
							context.startService(appInfo.intentDataService)
						} catch (e: Exception) {
							Log.w(TAG, "Error while starting data service ${appInfo.name}: $e")
						}
					}
				}
			}
		}
		return true
	}

	private fun prepareCarConnectionIntent(serviceIntent: Intent): Intent {
		val connectionIntent = Intent(serviceIntent)
		connectionIntent.putExtra("EXTRA_BRAND", iDriveConnectionStatus.brand)
		connectionIntent.putExtra("EXTRA_HOST", iDriveConnectionStatus.host)
		connectionIntent.putExtra("EXTRA_PORT", iDriveConnectionStatus.port)
		connectionIntent.putExtra("EXTRA_INSTANCE_ID", iDriveConnectionStatus.instanceId)
		return connectionIntent
	}

	fun stop() {
		synchronized(this) {
			val connectionBindings = ArrayList(boundConnectionAddons.keys)
			connectionBindings.forEach { appInfo ->
				val binding = boundConnectionAddons[appInfo]
				if (appInfo.intentConnectionService != null && binding != null) {
					Log.i(TAG, "Unbinding connection addon ${appInfo.name} ${appInfo.packageName}")
					try {
						context.unbindService(binding)
					} catch (e: IllegalArgumentException) {
						// complains for some reason, perhaps because the returned IBinder is null
					}
					try {
						context.stopService(appInfo.intentConnectionService)
					} catch (e: IllegalArgumentException) {
						Log.w(TAG, "Error while stopping service ${appInfo.name}: $e")
					}
				}
				boundDataAddons.remove(appInfo)
			}
			val dataBindings = ArrayList(boundDataAddons.keys)
			dataBindings.forEach { appInfo ->
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