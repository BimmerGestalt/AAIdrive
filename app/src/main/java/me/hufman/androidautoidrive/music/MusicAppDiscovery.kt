package me.hufman.androidautoidrive.music

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.support.v4.media.MediaBrowserServiceCompat
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import me.hufman.androidautoidrive.Analytics
import me.hufman.androidautoidrive.music.controllers.CombinedMusicAppController
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.lang.Runnable
import java.util.*
import kotlin.collections.HashSet
import kotlin.coroutines.CoroutineContext

class MusicAppDiscovery(val context: Context, val handler: Handler): CoroutineScope {
	override val coroutineContext: CoroutineContext
		get() = handler.asCoroutineDispatcher("MusicAppDiscovery")

	val TAG = "MusicAppDiscovery"

	val browseApps: MutableList<MusicAppInfo> = LinkedList()
	val combinedApps: MutableList<MusicAppInfo> = LinkedList()
	val validApps
		get() = combinedApps.filter {it.connectable || it.controllable}

	private val activeConnections = HashMap<MusicAppInfo, CombinedMusicAppController>()
	var listener: Runnable? = null

	val musicSessions = MusicSessions(context)

	val connectors = listOf(
			MusicBrowser.Connector(context, handler),
			SpotifyAppController.Connector(context)
	)

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
		this.combinedApps.clear()
		this.combinedApps.addAll(this.browseApps)

		val appsByName = this.combinedApps.associateBy { it.packageName }
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
				this.combinedApps.add(app)
			}
		}

		this.combinedApps.sortBy { it.name.toLowerCase() }

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
		disconnectApp(appInfo)  // clear any previous connection

		Log.i(TAG, "Testing ${appInfo.name} for connectivity")
		val controller = CombinedMusicAppController(connectors, appInfo)
		controller.subscribe {
			Log.d(TAG, "Received update about controller probe ${appInfo.name}: connectable=${controller.isConnected()} pending=${controller.isPending()}")
			appInfo.connectable = controller.isConnected()
			appInfo.playsearchable = controller.isSupportedAction(MusicAction.PLAY_FROM_SEARCH)
			// check if we have finished connecting to everything
			if (!controller.isPending()) {
				// do final probes
				launch {
					val browseResult = controller.browse(null)
					Log.d(TAG, "Received browse results from ${appInfo.name}: $browseResult")
					if (browseResult.isNotEmpty()) {
						appInfo.browseable = true
						listener?.run()
					}
					val searchResults = controller.search("query")
					if (searchResults?.isNotEmpty() == true) {
						appInfo.searchable = true
						listener?.run()
					}
					// save our cached version and stop probing
					disconnectApp(appInfo)
					appInfo.probed = true
					scheduleSave()
					Analytics.reportMusicAppProbe(appInfo)
					Log.i(TAG, "Finished probing: $appInfo")
					listener?.run()
				}
			}
		}
		activeConnections[appInfo] = controller
	}
}