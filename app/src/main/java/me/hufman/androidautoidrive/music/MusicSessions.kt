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
import me.hufman.androidautoidrive.carapp.notifications.NotificationListenerServiceImpl
import me.hufman.androidautoidrive.music.controllers.GenericMusicAppController
import me.hufman.androidautoidrive.music.controllers.MusicAppController
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MusicSessions(val context: Context) {
	companion object {
		const val TAG = "MusicSessions"
	}

	inner class Connector(val context: Context): MusicAppController.Connector {
		override fun connect(appInfo: MusicAppInfo): Observable<MusicAppController> {
			val pendingController = MutableObservable<MusicAppController>()
			sessionControllers[appInfo.packageName] = pendingController
			val session = connectApp(appInfo)
			if (session != null) {
				pendingController.value = GenericMusicAppController(context, session, null)
			}
			return pendingController
		}
	}

	val mediaManager = context.getSystemService(MediaSessionManager::class.java)
	val sessionControllers = ConcurrentHashMap<String, MutableObservable<MusicAppController>>()
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
		} catch (e: SecurityException) {
			// user hasn't granted Notification Access yet
			Log.w(TAG, "Can't connect to ${desiredApp.name}, user hasn't granted Notification Access yet")
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
				val actions = it.playbackState?.actions ?: 0
				actions and (ACTION_PLAY or ACTION_PAUSE) > 0
			}.groupBy { it.packageName }
			sessionControllers.forEach {
				val session = sessionsByName[it.key]?.firstOrNull()
				if (session != null) {
					val session = MediaControllerCompat(context, MediaSessionCompat.Token.fromToken(session.sessionToken))
					it.value.value = GenericMusicAppController(context, session, null)
				} else {
					it.value.value = null
				}
			}
		} catch (e: SecurityException) {
			// user hasn't granted Notification Access yet
		}
	}

	fun discoverApps(): List<MusicAppInfo> {
		return try {
			val sessions = mediaManager.getActiveSessions(ComponentName(context, NotificationListenerServiceImpl::class.java))
			return sessions.filter {
				val actions = it.playbackState?.actions ?: 0
				actions and (ACTION_PLAY or ACTION_PAUSE) > 0
			}.map {
				MusicAppInfo.getInstance(context, it.packageName, null).apply {
					this.controllable = true
					val actions = it.playbackState?.actions ?: 0
					this.playsearchable = actions and PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH > 0
				}
			}
		} catch (e: SecurityException) {
			// user hasn't granted Notification Access yet
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
				if (actions and (ACTION_PLAY or ACTION_PAUSE) > 0 && state == STATE_PLAYING) {
					Log.i(TAG, "Returning mediaSession ${session.packageName}")
					return MusicAppInfo.getInstance(context, session.packageName, null).apply {
						this.controllable = true
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