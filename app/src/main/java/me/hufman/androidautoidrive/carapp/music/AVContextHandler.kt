package me.hufman.androidautoidrive.carapp.music

import android.util.Log
import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.carapp.RHMIApplicationSynchronized
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

class AVContextHandler(val app: RHMIApplicationSynchronized, val controller: MusicController, val phoneAppResources: PhoneAppResources) {
	val TAG = "AVContextHandler"
	val carConnection = (app.unwrap() as RHMIApplicationEtch).remoteServer
	var amHandle: Int? = null
	var avHandle: Int? = null
	val knownApps = ConcurrentHashMap<String, MusicAppInfo>()
	@Volatile var currentContext = false  // whether we are the current playing app.. the car doesn't grantConnection if we are already connected

	fun updateApps(apps: List<MusicAppInfo>) {
		app.runSynchronized {
			val myIdent = "me.hufman.androidautoidrive.music"
			if (amHandle == null) {
				amHandle = carConnection.am_create("0", "\u0000\u0000\u0000\u0000\u0000\u0002\u0000\u0000".toByteArray())
				carConnection.am_addAppEventHandler(amHandle, myIdent)
			}

			for (app in apps) {
				if (!knownApps.containsKey(app.amAppIdentifier)) {
//					Thread.sleep(100)
					Log.i(TAG, "Creating am app for new app ${app.name}")
					carConnection.am_registerApp(amHandle, app.amAppIdentifier, getAMInfo(app))

					knownApps[app.amAppIdentifier] = app
				}
			}

			if (IDriveConnectionListener.instanceId == null) {
				Log.w(TAG, "instanceId is null! av handle won't be usable")
			} else {
				Log.d(TAG, "instanceId == ${IDriveConnectionListener.instanceId}")
			}
			if (avHandle == null) {
//				Thread.sleep(100)
				avHandle = carConnection.av_create(IDriveConnectionListener.instanceId
						?: 13, myIdent)
				Log.d(TAG, "AV handle: $avHandle")
//				Thread.sleep(100)
			}
		}

		synchronized(this) {
			if (currentContext && controller.currentApp == null) {
				Log.i(TAG, "Might have found an app to resume playback, checking..")
				reconnectApp()
			}
		}
	}

	/** What weight to assign for the AM app, to sort it in the list properly */
	fun getAppWeight(app: MusicAppInfo): Int {
		val name = app.name.toLowerCase().toCharArray().filter { it.isLetter() }
		var score = min(name[0].toInt() - 'a'.toInt(), 'z'.toInt())
		score = score * 6 + ((name[1].toInt() / 6.0).roundToInt())
		return score
	}

	fun getAMInfo(app: MusicAppInfo): Map<Int, Any> {
		val amInfo = mutableMapOf<Int, Any>(
			0 to 145,   // basecore version
			1 to app.name,  // app name
			2 to phoneAppResources.getBitmap(app.icon, 48, 48), // icon
			3 to "Multimedia",   // section
			4 to true,
			5 to 800 - getAppWeight(app),   // weight
			8 to -1  // mainstateId
		)
		// language translations, dunno which one is which
		for (languageCode in 101..123) {
			amInfo[languageCode] = app.name
		}

		return amInfo
	}

	fun av_requestContext(ident: String) {
		knownApps[ident]?.apply { av_requestContext(this) } ?:
			Log.w(TAG, "Wanted to requestContext for missing app $ident?")
	}
	fun av_requestContext(app: MusicAppInfo) {
		val setting = AppSettings[AppSettings.KEYS.AUDIO_ENABLE_CONTEXT]
		if (setting.toBoolean()) {
			Log.i(TAG, "Sending requestContext to car for ${app.name}")
			this.app.runSynchronized {
//				Thread.sleep(100)
				if (!currentContext) {
					carConnection.av_requestConnection(avHandle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT)
				}
//				Thread.sleep(100)
			}
			if (currentContext || IDriveConnectionListener.instanceId == null) {
				// start playback if we are the current AV context
				// or play anyways if we have the wrong instanceId
				// the car will respond with av_connectionDenied if instanceId is incorrect (null coalesced to a random guess)
				controller.connectApp(app)
				enactPlayerState(BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PLAY)
				av_playerStateChanged(avHandle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT, BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PLAY)
			}
		} else {
			// just assume the car has given us access, and play the app anyways
			controller.connectApp(app)
			enactPlayerState(BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PLAY)
			av_playerStateChanged(avHandle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT, BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PLAY)
		}
	}

	fun av_connectionGranted(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
		Log.i(TAG, "Car declares current audio connection to us")
		synchronized(this) {
			currentContext = true

			if (controller.currentApp == null) {
				Log.i(TAG, "Successful connection request, trying to remember which app was last playing")
				reconnectApp()
			}
			// otherwise, the controller.currentApp was set in an av_requestContext call
		}
	}

	fun reconnectApp() {
		synchronized(this) {
			val appName = controller.loadDesiredApp()
			val amAppIdentifier = amAppIdentifier(appName)
			val appInfo = knownApps[amAppIdentifier]
			if (appInfo != null) {
				Log.i(TAG, "Found previously-remembered app to resume playback: ${appInfo.packageName}")
				controller.connectApp(appInfo)
			} else if (appName != "") {
				Log.i(TAG, "Previously-remembered app $appName not found yet in MusicAppDiscovery")
			} else {
				Log.d(TAG, "No previously-remembered app")
			}
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
//			Thread.sleep(300)
			carConnection.av_playerStateChanged(handle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT, playerState)
		}
	}

	private fun enactPlayerState(playerState: BMWRemoting.AVPlayerState) {
		synchronized(this) {
			when (playerState) {
				BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PAUSE -> controller.pause()
				BMWRemoting.AVPlayerState.AV_PLAYERSTATE_STOP -> controller.pause()
				BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PLAY -> controller.play()
			}
		}
	}

	fun av_connectionDeactivated(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
		// the car is requesting the current app stop so that a different app can play
		// either another app within our own app (which won't trigger connectionGranted)
		// or another source entirely{
		Log.i(TAG, "Deactivating app currently-connected ${controller.currentApp?.musicAppInfo?.name}")
		synchronized(this) {
			controller.pause()
			currentContext = false
		}
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