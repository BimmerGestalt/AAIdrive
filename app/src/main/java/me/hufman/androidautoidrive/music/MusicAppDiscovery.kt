package me.hufman.androidautoidrive.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserServiceCompat
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.HashSet

class MusicAppDiscovery(val context: Context) {
	val TAG = "MusicAppDiscovery"
	val apps = LinkedList<MusicAppInfo>()
	val validApps = LinkedList<MusicAppInfo>()
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

	private fun probeApp(appInfo: MusicAppInfo) {
		Log.i(TAG, "Testing ${appInfo.name} for connectivity")
		val component = ComponentName(appInfo.packageName, appInfo.className)
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
					val browseResult = MusicBrowser(context, appInfo).browse(null)
					if (browseResult.isNotEmpty()) {
						appInfo.browseable = true
						listener?.run()
					}
				}
				GlobalScope.launch {
					val searchResult = MusicBrowser(context, appInfo).search("query")
					if (searchResult != null) {
						appInfo.searchable = true
						listener?.run()
					}
				}
			}

			override fun onConnectionFailed() {
					appInfo.connectable = false
					Log.i(TAG, "Failed to connect to ${appInfo.name}")
				}
			}, null
		)
		mediaBrowser.connect()
	}
}