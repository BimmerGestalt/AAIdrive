package me.hufman.androidautoidrive.music

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import me.hufman.androidautoidrive.carapp.notifications.NotificationListenerServiceImpl
import java.util.*

class MusicSessions(val context: Context) {
	companion object {
		const val TAG = "MusicSessions"
	}

	val mediaManager = context.getSystemService(MediaSessionManager::class.java)

	var mediaController: MediaControllerCompat? = null

	fun connectApp(desiredApp: MusicAppInfo) {
		try {
			val sessions = mediaManager.getActiveSessions(ComponentName(context, NotificationListenerServiceImpl::class.java))
			for (session in sessions) {
				if (session.packageName == desiredApp.packageName) {
					mediaController = MediaControllerCompat(context, MediaSessionCompat.Token.fromToken(session.sessionToken))
					return
				}
			}
		} catch (e: SecurityException) {
			// user hasn't granted Notification Access yet
			Log.w(TAG, "Can't connect to ${desiredApp.name}, user hasn't granted Notification Access yet")
		}
	}

	fun discoverApps(): List<MusicAppInfo> {
		return try {
			val sessions = mediaManager.getActiveSessions(ComponentName(context, NotificationListenerServiceImpl::class.java))
			return sessions.map {
				MusicAppInfo.getInstance(context, it.packageName, "UNAVAILABLE").apply {
					this.controllable = true
				}
			}
		} catch (e: SecurityException) {
			// user hasn't granted Notification Access yet
			Log.i(TAG, "Can't discoverApps, user hasn't granted Notification Access yet")
			LinkedList()
		}
	}
}