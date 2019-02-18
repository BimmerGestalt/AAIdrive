package me.hufman.androidautoidrive.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserServiceCompat
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.HashSet

class MusicAppDiscovery(val context: Context, val handler: Handler) {
	val TAG = "MusicAppDiscovery"
	val apps:MutableList<MusicAppInfo> = LinkedList()
	val validApps:MutableList<MusicAppInfo> = LinkedList()
	private val activeConnections = HashMap<MusicAppInfo, MediaBrowserCompat>()
	var listener: Runnable? = null

	fun discoverApps() {
		val discoveredApps = HashSet<MusicAppInfo>()
		val previousApps = HashSet<MusicAppInfo>(apps)

		val packageManager = context.packageManager
		val intent = Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE)
		val services = packageManager.queryIntentServices(intent, 0)
		services.forEach {
			val appInfo = it.serviceInfo.applicationInfo
			val name = packageManager.getApplicationLabel(appInfo).toString()
			val icon = packageManager.getApplicationIcon(appInfo)
			val packageName = appInfo.packageName
			val className = it.serviceInfo.name

			Log.i(TAG, "Found music app $name")
			val musicAppInfo = MusicAppInfo(name, icon, packageName, className)
			discoveredApps.add(musicAppInfo)
			probeApp(musicAppInfo)
		}

		// clear out any old apps
		var changed = false
		for (app in previousApps) {
			if (!discoveredApps.contains(app)) {
				Log.i(TAG, "Removing previously-discovered app that has disappeared ${app.name}")
				changed = true
				this.apps.remove(app)
				this.validApps.remove(app)
			}
		}

		// add any new apps
		for (app in discoveredApps) {
			if (!previousApps.contains(app)) {
				Log.i(TAG, "Adding newly-discovered app ${app.name}")
				changed = true
				this.apps.add(app)
			}
		}

		apps.sortBy { it.name.toLowerCase() }

		if (changed) listener?.run()
	}

	fun cancelDiscovery() {
		for (app in apps) {
			disconnectApp(app)
		}
	}
	private fun disconnectApp(appInfo: MusicAppInfo) {
		val connection = activeConnections[appInfo]
		connection?.disconnect()
		activeConnections.remove(appInfo)
	}

	private fun probeApp(appInfo: MusicAppInfo) {
		Log.i(TAG, "Testing ${appInfo.name} for connectivity")
		val component = ComponentName(appInfo.packageName, appInfo.className)

		disconnectApp(appInfo)  // clear any previous connectino
		val mediaBrowser = MediaBrowserCompat(
				context, component, object: MediaBrowserCompat.ConnectionCallback() {
			override fun onConnected() {
				Log.i(TAG, "Successfully connected to ${appInfo.name}")
				appInfo.connectable = true
				validApps.add(appInfo)
				validApps.sortBy { it.name.toLowerCase() }
				listener?.run()

				// check for browse and searching
				GlobalScope.launch {
					val browseJob = launch {
						val browseResult = MusicBrowser(context, handler, appInfo).browse(null)
						if (browseResult.isNotEmpty()) {
							appInfo.browseable = true
							listener?.run()
						}
					}
					val searchJob = launch {
						val searchResult = MusicBrowser(context, handler, appInfo).search("query")
						if (searchResult != null) {
							appInfo.searchable = true
							listener?.run()
						}
					}
					browseJob.join()
					searchJob.join()
					disconnectApp(appInfo)
				}
			}

			override fun onConnectionFailed() {
				appInfo.connectable = false
				Log.i(TAG, "Failed to connect to ${appInfo.name}")
				disconnectApp(appInfo)
			}
			}, null
		)
		activeConnections[appInfo] = mediaBrowser
		mediaBrowser.connect()
	}
}