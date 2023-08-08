package me.hufman.androidautoidrive.music

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState.*
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import me.hufman.androidautoidrive.MutableObservable
import me.hufman.androidautoidrive.Observable
import me.hufman.androidautoidrive.music.controllers.GenericMusicAppController
import me.hufman.androidautoidrive.music.controllers.MusicAppController
import me.hufman.androidautoidrive.notifications.NotificationListenerServiceImpl
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MusicSessions(val context: Context) {
	companion object {
		const val TAG = "MusicSessions"
		var hasPermission = false

		private fun isControllable(actions: Long): Boolean {
			return ((actions and ACTION_PLAY) or (actions and ACTION_PAUSE) or (actions and ACTION_PLAY_FROM_SEARCH)) > 0
		}
	}

	inner class Connector(val context: Context): MusicAppController.Connector {
		override fun connect(appInfo: MusicAppInfo): Observable<GenericMusicAppController> {
			val pendingController = MutableObservable<GenericMusicAppController>()
			sessionControllers[appInfo.packageName] = pendingController
			val session = connectApp(appInfo)
			if (session != null) {
				pendingController.value = GenericMusicAppController(context, session, null)
			} else {
				pendingController.value = null
			}

			// set up MediaSession listener
			try {
				mediaManager.removeOnActiveSessionsChangedListener(sessionListener)
				mediaManager.addOnActiveSessionsChangedListener(sessionListener, ComponentName(context, NotificationListenerServiceImpl::class.java))
			} catch (e: SecurityException) {
				// user hasn't granted Notification Access yet
				Log.w(TAG, "Can't connect to ${appInfo.name}, user hasn't granted Notification Access yet")
			}

			return pendingController
		}
	}

	val mediaManager: MediaSessionManager = context.getSystemService(MediaSessionManager::class.java)
	val sessionControllers = ConcurrentHashMap<String, MutableObservable<GenericMusicAppController>>()
	val sessionListener = object: MediaSessionManager.OnActiveSessionsChangedListener {
		override fun onActiveSessionsChanged(p0: MutableList<MediaController>?) {
			updateAppControllers()
			sessionCallback?.run()
		}
	}
	var sessionCallback: Runnable? = null

	fun connectApp(desiredApp: MusicAppInfo): MediaControllerCompat? {
		try {
			val sessions = mediaManager.getActiveSessions(ComponentName(context, NotificationListenerServiceImpl::class.java))
			for (session in sessions) {
				if (session.packageName == desiredApp.packageName) {
					return MediaControllerCompat(context, MediaSessionCompat.Token.fromToken(session.sessionToken))
				}
			}
			hasPermission = true
		} catch (e: SecurityException) {
			// user hasn't granted Notification Access yet
			Log.w(TAG, "Can't connect to ${desiredApp.name}, user hasn't granted Notification Access yet")
			hasPermission = false
		}
		return null
	}

	/**
	 * Iterate through the music sessions and any MusicAppControllers and update connected status
	 */
	private fun updateAppControllers() {
		try {
			val sessions = mediaManager.getActiveSessions(ComponentName(context, NotificationListenerServiceImpl::class.java))
			val sessionsByName = sessions.filter {
				isControllable(it.playbackState?.actions ?: 0)
			}.groupBy { it.packageName }

			sessionControllers.forEach {
				val oldController = it.value.value
				val session = sessionsByName[it.key]?.firstOrNull()
				if (session != null) {
					if (oldController?.connected != true) {
						oldController?.disconnect()
						val mediaController = MediaControllerCompat(context, MediaSessionCompat.Token.fromToken(session.sessionToken))
						it.value.value = GenericMusicAppController(context, mediaController, null)
					}
				} else {
					oldController?.disconnect()
					it.value.value = null
				}
			}
		} catch (e: SecurityException) {
			// user hasn't granted Notification Access yet
			// disconnect any existing controllers
			sessionControllers.forEach {
				val oldController = it.value.value
				oldController?.disconnect()
				it.value.value = null
			}
		}
	}

	fun discoverApps(): List<MusicAppInfo> {
		return try {
			val sessions = mediaManager.getActiveSessions(ComponentName(context, NotificationListenerServiceImpl::class.java))
			hasPermission = true
			return sessions.filter {
				isControllable(it.playbackState?.actions ?: 0)
			}.mapNotNull {
				MusicAppInfo.getInstance(context, it.packageName, null)?.apply {
					this.controllable = true
					val actions = it.playbackState?.actions ?: 0
					this.playsearchable = actions and PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH > 0
				}
			}
		} catch (e: SecurityException) {
			// user hasn't granted Notification Access yet
			hasPermission = false
			Log.i(TAG, "Can't discoverApps, user hasn't granted Notification Access yet")
			LinkedList()
		}
	}

	/** Returns the music session that is playing music, if any */
	fun getPlayingApp(): MusicAppInfo? {
		try {
			val sessions = mediaManager.getActiveSessions(ComponentName(context, NotificationListenerServiceImpl::class.java))
			for (session in sessions) {
				val actions = session.playbackState?.actions ?: 0
				val state = session.playbackState?.state ?: 0
				if (isControllable(actions) && state == STATE_PLAYING) {
					Log.i(TAG, "Found mediaSession for ${session.packageName}")
					val appInfo = MusicAppInfo.getInstance(context, session.packageName, null)?.apply {
						this.controllable = true
					}
					if (appInfo != null) {
						return appInfo
					} else {
						Log.w(TAG, "Failed to load MusicAppInfo for ${session.packageName}")
					}
				}
			}
		} catch (e: SecurityException) {
			// user hasn't granted Notification Access yet
		}
		return null
	}

	/**
	 * Registers this runnable to be called whenever the media sessions change
	 * It may not succeed, if the user hasn't granted permission, so just try repeatedly
	 */
	fun registerCallback(runnable: Runnable) {
		unregisterCallback()
		try {
			mediaManager.addOnActiveSessionsChangedListener(sessionListener, ComponentName(context, NotificationListenerServiceImpl::class.java))
			sessionCallback = runnable
		} catch (e: SecurityException) {

		}
	}

	fun unregisterCallback() {
		mediaManager.removeOnActiveSessionsChangedListener(sessionListener)
		sessionCallback = null
	}
}