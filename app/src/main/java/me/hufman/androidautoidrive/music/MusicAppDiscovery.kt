package me.hufman.androidautoidrive.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.hufman.androidautoidrive.Analytics
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.*
import kotlin.collections.HashSet

class MusicAppDiscovery(val context: Context, val handler: Handler) {
	val TAG = "MusicAppDiscovery"

	private val browseApps: MutableList<MusicAppInfo> = LinkedList()
	val combinedApps: MutableList<MusicAppInfo> = Collections.synchronizedList(LinkedList())
	val validApps
		get() = combinedApps.filter {it.connectable || it.controllable}

	private val activeConnections = HashMap<MusicAppInfo, MediaBrowserCompat>()
	var listener: Runnable? = null

	val musicSessions = MusicSessions(context)

	private val saveCacheTask = Runnable {
		saveCache()
	}

	fun loadCache() {
		val appsByName = browseApps.associateBy { it.packageName }
		try {
			context.openFileInput("app_discovery.json").use {
				val data = it.readBytes().toString(Charsets.UTF_8)

				val json = JSONArray(data)
				for (i in 0 until json.length()) {
					val jsonData = json.getJSONObject(i)
					val packageName = jsonData.getString("packageName")
					val app = appsByName[packageName]
					if (app != null) {
						app.probed = true
						app.connectable = jsonData.getBoolean("connectable")
						app.browseable = jsonData.getBoolean("browseable")
						app.searchable = jsonData.getBoolean("searchable")
						app.playsearchable = jsonData.getBoolean("playsearchable")

					}
				}
			}
		} catch (e: IOException) {
			// missing file
		} catch (e: Exception) {
			// invalid json
			Log.d(TAG, "Failed to load app cache: ${e.message}")
		}
	}

	fun scheduleSave() {
		handler.removeCallbacks(saveCacheTask)
		handler.postDelayed(saveCacheTask, 1000)
	}

	fun saveCache() {
		val json = JSONArray().apply {
			browseApps.forEach {
				this.put(JSONObject(it.toMap()))
			}
		}
		context.openFileOutput("app_discovery.json", Context.MODE_PRIVATE).use {
			it.write(json.toString().toByteArray(Charsets.UTF_8))
		}
	}

	/**
	 * Discover what apps are installed on the phone that implement MediaBrowserServer
	 * Loads a cache of previous probe results
	 * Also adds a list of active Media Sessions
	 */
	fun discoverApps() {
		val discoveredApps = HashSet<MusicAppInfo>()
		val previousApps = HashSet<MusicAppInfo>(browseApps)

		val packageManager = context.packageManager
		val intent = Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE)
		val services = packageManager.queryIntentServices(intent, 0)
		discoveredApps.addAll(services.map {
			val appInfo = it.serviceInfo.applicationInfo
			val name = packageManager.getApplicationLabel(appInfo).toString()
			Log.i(TAG, "Found music app $name")
			MusicAppInfo.getInstance(context, appInfo.packageName, it.serviceInfo.name)
		})

		// clear out any old apps
		for (app in previousApps) {
			if (!discoveredApps.contains(app)) {
				this.browseApps.remove(app)
			}
		}

		// add any new apps
		for (app in discoveredApps) {
			if (!previousApps.contains(app)) {
				this.browseApps.add(app)
			}
		}

		loadCache() // load previously-probed states

		this.browseApps.sortBy { it.name.toLowerCase() }

		// load up music session info, and register for updates
		addSessionApps()
		musicSessions.registerCallback(Runnable { addSessionApps() })
	}

	/**
	 * Run the discovery from the given handler thread
	 * This can be used to initiate discovery from a car event callback thread
	 */
	fun discoverAppsAsync() {
		handler.post { discoverApps() }
	}

	fun addSessionApps() {
		// discover Music Sessions
		val apps = ArrayList(this.browseApps)

		val appsByName = apps.associateBy { it.packageName }
		val mediaSessionApps = musicSessions.discoverApps()
		for (app in mediaSessionApps) {
			Log.i(TAG, "Found music session ${app.name}")
			val discoveredApp = appsByName[app.packageName]
			if (discoveredApp != null) {
				// update this discovered app to be controllable
				discoveredApp.controllable = true
			} else {
				// don't try to probe this new app, it doesn't advertise browse support
				app.probed = true
				apps.add(app)
			}
		}
		apps.sortBy { it.name.toLowerCase() }

		synchronized(this.combinedApps) {
			this.combinedApps.clear()
			this.combinedApps.addAll(apps)
		}

		handler.post {
			listener?.run()
		}
	}

	/**
	 * Probe the discovered apps for connectable/browseable status
	 * the force flag sets whether to probe every app again or just new apps
	 */
	fun probeApps(force: Boolean = true) {
		// probe all apps
		for (app in this.browseApps) {
			if (force || !app.probed) {
				probeApp(app)
			}
		}
	}

	fun cancelDiscovery() {
		for (app in browseApps) {
			disconnectApp(app)
		}
		musicSessions.unregisterCallback()
	}

	private fun disconnectApp(appInfo: MusicAppInfo) {
		val connection = activeConnections[appInfo]
		connection?.disconnect()
		activeConnections.remove(appInfo)
	}

	private fun probeApp(appInfo: MusicAppInfo) {
		if (Looper.myLooper() != handler.looper) {
			handler.post {
				probeApp(appInfo)
			}
			return
		}

		Log.i(TAG, "Testing ${appInfo.name} for connectivity")
		val component = ComponentName(appInfo.packageName, appInfo.className)

		disconnectApp(appInfo)  // clear any previous connection

		var mediaBrowser: MediaBrowserCompat? = null
		val connectionCallback = object: MediaBrowserCompat.ConnectionCallback() {
			override fun onConnected() {
				Log.i(TAG, "Successfully connected to ${appInfo.name}")
				appInfo.connectable = true
				listener?.run()

				// detect if the app can play from search
				val sessionToken = mediaBrowser?.sessionToken
				if (sessionToken != null) {
					val controller = MediaControllerCompat(context, sessionToken)
					val actions = controller.playbackState?.actions ?: 0
					appInfo.playsearchable = actions and PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH > 0
				}

				// check for browse and searching
				GlobalScope.launch {
					val browseJob = launch {
						val browser = MusicBrowser(context, handler, appInfo)
						val browseResult = browser.browse(null)
						if (browseResult.isNotEmpty()) {
							appInfo.browseable = true
							listener?.run()
						}
						browser.disconnect()
					}
					val searchJob = launch {
						val browser = MusicBrowser(context, handler, appInfo)
						val searchResult = browser.search("query")
						if (searchResult != null) {
							appInfo.searchable = true
							listener?.run()
						}
						browser.disconnect()
					}
					browseJob.join()
					searchJob.join()
					disconnectApp(appInfo)
					appInfo.probed = true
					scheduleSave()
					Analytics.reportMusicAppProbe(appInfo)
					Log.i(TAG, "Finished probing: $appInfo")
				}
			}

			override fun onConnectionFailed() {
				Log.i(TAG, "Failed to connect to ${appInfo.name}")
				disconnectApp(appInfo)
				appInfo.probed = true
				appInfo.connectable = false
				scheduleSave()
				Analytics.reportMusicAppProbe(appInfo)
			}
		}
		mediaBrowser = MediaBrowserCompat(context, component, connectionCallback, null)
		activeConnections[appInfo] = mediaBrowser
		mediaBrowser.connect()
	}
}