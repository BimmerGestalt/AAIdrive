package me.hufman.androidautoidrive.carapp.music

import android.util.Log
import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.GraphicsHelpers
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationSynchronized
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationEtch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.math.roundToInt

fun amAppIdentifier(packageName: String): String {
	return "androidautoidrive.$packageName"
}
val MusicAppInfo.amAppIdentifier: String
	get() = amAppIdentifier(this.packageName)

class AVContextHandler(val app: RHMIApplicationSynchronized, val controller: MusicController, val graphicsHelpers: GraphicsHelpers, val musicAppMode: MusicAppMode) {
	val MY_IDENT = "me.hufman.androidautoidrive.music"  // AM and AV ident string
	val TAG = "AVContextHandler"
	val carConnection = (app.unwrap() as RHMIApplicationEtch).remoteServer
	var amHandle: Int? = null
	var avHandle: Int? = null
	val knownApps = ConcurrentHashMap<String, MusicAppInfo>()
	@Volatile var currentContext = false  // whether we are the current playing app.. the car doesn't grantConnection if we are already connected

	/**
	 * Creates the avHandle
	 * Should be run inside a car's synchronized block
	 */
	fun createAvHandle() {
		if (avHandle != null) {
			// already done
			return
		}
		val instanceId = IDriveConnectionListener.instanceId
		if (instanceId == null) {
			Log.w(TAG, "instanceId is null! skipping av handle creation for now")
		} else {
			Log.d(TAG, "instanceId == ${IDriveConnectionListener.instanceId}")
			avHandle = carConnection.av_create(instanceId, MY_IDENT)
			Log.d(TAG, "AV handle: $avHandle")
		}
	}

	fun updateApps(apps: List<MusicAppInfo>) {
		app.runSynchronized {
			if (amHandle == null) {
				amHandle = carConnection.am_create("0", "\u0000\u0000\u0000\u0000\u0000\u0002\u0000\u0000".toByteArray())
				carConnection.am_addAppEventHandler(amHandle, MY_IDENT)
			}

			for (app in apps) {
				if (!knownApps.containsKey(app.amAppIdentifier)) {
					knownApps[app.amAppIdentifier] = app

					if (musicAppMode.shouldId5Playback() && app.packageName == "com.spotify.music") {
						continue    // don't create an AM for Spotify, we have the real icon
					}
					Log.i(TAG, "Creating am app for new app ${app.name}")
					carConnection.am_registerApp(amHandle, app.amAppIdentifier, getAMInfo(app))

				}
			}

			createAvHandle()
		}

		if (currentContext && controller.currentAppController == null) {
			Log.i(TAG, "Car automatically requested to resume playback from a disconnect")
			reconnectApp()
		}
	}

	/**
	 * Recreate an AM app entry, which removes the spinning animation
	 */
	fun amRecreateApp(appInfo: MusicAppInfo) {
		amHandle ?: return
		app.runSynchronized {
			try {
				carConnection.am_registerApp(amHandle, appInfo.amAppIdentifier, getAMInfo(appInfo))
			} catch (e: Exception) {
				Log.w(TAG, "Received exception during AM app redraw", e)
			}
		}
	}

	/** What weight to assign for the AM app, to sort it in the list properly */
	fun getAppWeight(appName: String): Int {
		val name = appName.toLowerCase().toCharArray().filter { it.isLetter() }
		var score = min(name[0].toInt() - 'a'.toInt(), 'z'.toInt())
		score = score * 6 + ((name[1].toInt() / 6.0).roundToInt())
		return score
	}

	fun getAMInfo(app: MusicAppInfo): Map<Int, Any> {
		val adjustment = if (musicAppMode.shouldId5Playback()) { getAppWeight("Spotify") - (800 - 500) } else 0
		val amInfo = mutableMapOf<Int, Any>(
			0 to 145,   // basecore version
			1 to app.name,  // app name
			2 to graphicsHelpers.compress(app.icon, 48, 48), // icon
			3 to "Multimedia",   // section
			4 to true,
			5 to 800 - (getAppWeight(app.name) - adjustment),   // weight
			8 to -1  // mainstateId
		)
		// language translations, dunno which one is which
		for (languageCode in 101..123) {
			amInfo[languageCode] = app.name
		}

		return amInfo
	}

	fun getAppInfo(appId: String): MusicAppInfo? {
		return knownApps[appId]
	}

	fun av_requestContext(app: MusicAppInfo) {
		controller.connectAppManually(app)  // prepare the music controller, so that av_connectionGranted can use it
		if (musicAppMode.shouldRequestAudioContext()) {
			this.app.runSynchronized {
				createAvHandle()    // make sure we have an avHandle
				val avHandle = avHandle
				if (!currentContext && avHandle != null) {
					Log.i(TAG, "Sending requestContext to car for ${app.name}")
					carConnection.av_requestConnection(avHandle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT)
				} else if (!currentContext && avHandle == null) {
					Log.i(TAG, "avHandle is not set up yet, not requesting context")
				}
			}
			if (currentContext || avHandle == null) {
				// start playback if we are the current AV context
				// or play anyways if we have the wrong instanceId
				// the car will respond with av_connectionDenied if instanceId is incorrect (null coalesced to a random guess)
				enactPlayerState(BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PLAY)
				av_playerStateChanged(avHandle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT, BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PLAY)
			}
		} else {
			// acting as just a fancy controller for Bluetooth music, just play the app
			enactPlayerState(BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PLAY)
		}
	}

	fun av_connectionGranted(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
		Log.i(TAG, "Car declares current audio connection to us")
		currentContext = true

		if (controller.currentAppInfo == null) {
			Log.i(TAG, "Successful connection request, trying to remember which app was last playing")
			reconnectApp()
		}
		val desiredAppInfo = controller.currentAppInfo
		if (desiredAppInfo != null && controller.currentAppController == null) {
			// MusicController wants to play an app, but the controller isn't ready yet
			controller.connectAppAutomatically(desiredAppInfo)
		}
		// otherwise, the controller.currentApp was set in an av_requestContext call
	}

	/** When the car asks us to start playing, pick an app */
	fun reconnectApp() {
		val nowPlaying = controller.musicSessions.getPlayingApp()
		if (nowPlaying != null) {
			// the controller will have already connected, from the MusicAppDiscovery thread
			Log.i(TAG, "Found already-playing app while resuming car playback: ${nowPlaying.packageName}")
			return
		}
		val appName = controller.loadDesiredApp()
		val amAppIdentifier = amAppIdentifier(appName)
		val appInfo = knownApps[amAppIdentifier]
		if (appInfo != null) {
			Log.i(TAG, "Found previously-remembered app to resume playback: ${appInfo.packageName}")
			controller.connectAppAutomatically(appInfo)
		} else if (appName != "") {
			Log.i(TAG, "Previously-remembered app $appName not found yet in MusicAppDiscovery")
		} else {
			Log.d(TAG, "No previously-remembered app")
		}
	}

	fun av_requestPlayerState(handle: Int?, connectionType: BMWRemoting.AVConnectionType?, playerState: BMWRemoting.AVPlayerState?) {
		Log.i(TAG, "Received requestPlayerState $playerState")
		if (playerState != null) {
			enactPlayerState(playerState)
			// slightly cheating, telling the car that we are playing without being certain the app is connected
			av_playerStateChanged(handle, connectionType, playerState)
		}
	}

	private fun av_playerStateChanged(handle: Int?, connectionType: BMWRemoting.AVConnectionType?, playerState: BMWRemoting.AVPlayerState?) {
		// helper function to help synchronize car accesses
		app.runSynchronized {
			if (handle != null) {
				carConnection.av_playerStateChanged(handle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT, playerState)
			}
		}
	}

	private fun enactPlayerState(playerState: BMWRemoting.AVPlayerState) {
		when (playerState) {
			BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PAUSE -> controller.pause()
			BMWRemoting.AVPlayerState.AV_PLAYERSTATE_STOP -> controller.pause()
			BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PLAY -> controller.play()
		}
	}

	fun av_connectionDeactivated(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
		// the car is requesting the current app stop so that a different app can play
		// either another app within our own app (which won't trigger connectionGranted)
		// or another source entirely{
		Log.i(TAG, "Deactivating app currently-connected ${controller.currentAppInfo?.name}")
		controller.pause()
		currentContext = false
	}

	fun av_multimediaButtonEvent(handle: Int?, event: BMWRemoting.AVButtonEvent?) {
		when (event) {
			BMWRemoting.AVButtonEvent.AV_EVENT_SKIP_UP -> controller.skipToNext()
			BMWRemoting.AVButtonEvent.AV_EVENT_SKIP_DOWN -> controller.skipToPrevious()
			BMWRemoting.AVButtonEvent.AV_EVENT_SKIP_LONG_UP -> controller.startFastForward()
			BMWRemoting.AVButtonEvent.AV_EVENT_SKIP_LONG_DOWN -> controller.startRewind()
			BMWRemoting.AVButtonEvent.AV_EVENT_SKIP_LONG_STOP -> controller.stopSeeking()
		}
	}
}