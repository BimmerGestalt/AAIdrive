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

class AVContextHandler(val app: RHMIApplicationSynchronized, val controller: MusicController, val phoneAppResources: PhoneAppResources) {
	val TAG = "AVContextHandler"
	val carConnection = (app.unwrap() as RHMIApplicationEtch).remoteServer
	var mainHandle: Int? = null
	val knownApps = ConcurrentHashMap<MusicAppInfo, Int>()
	var amHandle: Int? = null
	val appHandles = ConcurrentHashMap<Int, MusicAppInfo>()
	var currentContext = false  // whether we are the current playing app.. the car doesn't grantConnection if we are already connected
	var desiredApp: MusicAppInfo? = null    // the app that we want to play, user selected or by the car requesting playback
	var desiredStates = ConcurrentHashMap<MusicAppInfo, BMWRemoting.AVPlayerState>()

	fun updateApps(apps: List<MusicAppInfo>) {
		app.runSynchronized {
			for (app in knownApps.keys) {
				if (!apps.contains(app)) {
					Log.i(TAG, "Disposing avHandle for old app ${app.name}")
					val oldHandle = knownApps.remove(app)
					if (oldHandle != null) {
						carConnection.av_dispose(oldHandle)
					}
					appHandles.remove(oldHandle)
				}
			}

			val myIdent = "me.hufman.androidautoidrive.music"
			if (amHandle == null) {
				amHandle = carConnection.am_create("0", "\u0000\u0000\u0000\u0000\u0002\u0000\u0000".toByteArray())
				carConnection.am_addAppEventHandler(amHandle, myIdent)
			}

			if (IDriveConnectionListener.instanceId == null) {
				Log.w(TAG, "instanceId is null! av handles won't be usable")
			} else {
				Log.d(TAG, "instanceId == ${IDriveConnectionListener.instanceId}")
			}
			if (mainHandle == null) {
					mainHandle = carConnection.av_create(IDriveConnectionListener.instanceId ?: 8, myIdent)
			}
			for (app in apps) {
				if (!knownApps.containsKey(app)) {
					Log.i(TAG, "Creating avHandle for new app ${app.name}")
					val appId = "androidautoidrive.${app.packageName}"
					val handle = carConnection.av_create(IDriveConnectionListener.instanceId
							?: 8, appId)
					knownApps[app] = handle
					appHandles[handle] = app

					carConnection.am_registerApp(amHandle, appId, getAMInfo(app))
				}
			}
		}
	}

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
		// language translations
		for (languageCode in 101..123) {
			amInfo[languageCode] = app.name
		}

		return amInfo
	}

	fun av_requestContext(ident: String) {
		knownApps.keys.firstOrNull { ident == "androidautoidrive.${it.packageName}" }?.apply { av_requestContext(this) } ?:
		Log.w(TAG, "Wanted to requestContext for missing app ${ident}?")
	}
	fun av_requestContext(app: MusicAppInfo) {
		val handle = knownApps[app]
		if (handle == null) {
			Log.w(TAG, "Wanted to requestContext for unregistered app ${app.name}?")
			return
		}
		synchronized(this) {
			desiredApp = app
		}
		if (controller.currentApp?.musicAppInfo != app) {   // if switching apps and
			controller.disconnectApp()  // if the car sends a STOP command for the old app, ignore it
		}
		val setting = AppSettings[AppSettings.KEYS.AUDIO_ENABLE_CONTEXT]
		if (setting.toBoolean()) {
			Log.i(TAG, "Sending requestContext to car for ${app.name}")
			this.app.runSynchronized {
				if (!currentContext) {
					carConnection.av_requestConnection(mainHandle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT)
				}
				carConnection.av_requestConnection(handle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT)
			}
			if (currentContext || IDriveConnectionListener.instanceId == null) {
				// start playback anyways
				// the car will respond with av_connectionDenied if instanceId is incorrect (null coalesced to a random guess)
				controller.connectApp(app)
				controller.play()
				av_playerStateChanged(handle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT, BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PLAY)
			}
		} else {
			// just assume the car has given us access, and play the app anyways
			controller.connectApp(app)
			controller.play()
			av_playerStateChanged(handle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT, BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PLAY)
		}
	}

	fun av_connectionGranted(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
		if (handle == mainHandle) {
			// claimed av context from some other source, trigger our actual requested context
			Log.i(TAG, "Successful connection request for the main app handle $mainHandle, requesting our desired app ${knownApps[desiredApp]}")
			val desiredHandle = knownApps[desiredApp] ?: return
			app.runSynchronized {
				carConnection.av_requestConnection(desiredHandle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT)
			}
			return
		}
		val app = appHandles[handle]
		if (app == null) {
			Log.w(TAG, "Successful connection request for unknown app handle $handle?")
			return
		}

		val desiredState = synchronized(this) {
			currentContext = true
			this.desiredApp = app
			this.desiredStates[app]
		}

		Log.i(TAG, "Car declares current audio connection to ${app.name}, setting connection")
		controller.connectApp(app)

		if (desiredState != null) {
			enactPlayerState(desiredState)
			av_playerStateChanged(handle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT, BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PLAY)
		} else {
			Log.i(TAG, "Unknown playback state for app $app, waiting for av_requestPlayerState")
		}
	}

	fun av_requestPlayerState(handle: Int?, connectionType: BMWRemoting.AVConnectionType?, playerState: BMWRemoting.AVPlayerState?) {
		val app = appHandles[handle]
		Log.i(TAG, "Received requestPlayerState $playerState for ${app?.name}")
		if (app != null && playerState != null) {
			if (playerState == BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PLAY) {
				desiredStates[app] = playerState
			}
			// If we are currently connected to this app
			if (controller.currentApp?.musicAppInfo == app) {
				// we are currently connected to this app
				enactPlayerState(playerState)
			} else if (desiredApp == app) {
				// we aren't connected currently, but we want to be
				enactPlayerState(playerState)
			} else if (desiredApp != null) {
				// The user selected a different app, don't enact anything
			} else if (desiredApp == null) {
				// no desiredApp is set yet, remember for connectionGranted
			} else {
				Log.w(TAG, "Unknown state! desiredApp=${desiredApp?.name} connectedApp=${controller.currentApp}")
			}
			av_playerStateChanged(handle, connectionType, playerState)
		}
	}

	private fun av_playerStateChanged(handle: Int?, connectionType: BMWRemoting.AVConnectionType?, playerState: BMWRemoting.AVPlayerState?) {
		// helper function to help synchronize car accesses
		app.runSynchronized {
			carConnection.av_playerStateChanged(handle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT, playerState)
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
		// or another source entirely
		val app = appHandles[handle]
		if (controller.currentApp?.musicAppInfo == app) {
			Log.i(TAG, "Deactivating app currently-connected ${app?.name}")
			controller.pause()
		} else {
			Log.i(TAG, "Deactivating app ${app?.name}, which isn't our current connection")
		}
		desiredStates.remove(app)   // forget any saved state for this app
		synchronized(this) {
			if (desiredApp == app) {
				// User didn't select a different app, so the car is switching away
				Log.i(TAG, "Detecting our loss of audio focus")
				currentContext = false
			}
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