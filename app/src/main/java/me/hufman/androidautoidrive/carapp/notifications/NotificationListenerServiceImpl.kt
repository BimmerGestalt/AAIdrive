package me.hufman.androidautoidrive.carapp.notifications

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import me.hufman.androidautoidrive.carapp.notifications.ParseNotification.dumpNotification
import me.hufman.androidautoidrive.carapp.notifications.ParseNotification.shouldPopupNotification
import me.hufman.androidautoidrive.carapp.notifications.ParseNotification.shouldShowNotification
import me.hufman.androidautoidrive.carapp.notifications.ParseNotification.summarizeNotification
import me.hufman.androidautoidrive.phoneui.UIState
import me.hufman.androidautoidrive.phoneui.MainActivity
import me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications.Companion.EXTRA_NOTIFICATION
import me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications.Companion.INTENT_NEW_NOTIFICATION
import me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications.Companion.INTENT_UPDATE_NOTIFICATIONS
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener

fun Notification.isGroupSummary(): Boolean {
	val FLAG_GROUP_SUMMARY = 0x00000200     // hard-coded to work on old SDK
	return this.group != null && (this.flags and FLAG_GROUP_SUMMARY) != 0
}

class NotificationListenerServiceImpl: NotificationListenerService() {
	companion object {
		const val TAG = "IDriveNotifications"
		const val LOG_NOTIFICATIONS = false
		const val INTENT_INTERACTION = "me.hufman.androidaudoidrive.PhoneNotificationUpdate.INTERACTION"
		const val INTENT_REQUEST_DATA = "me.hufman.androidaudoidrive.PhoneNotificationUpdate.REQUEST_DATA"
		const val EXTRA_KEY = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotificationUpdate.EXTRA_KEY"
		const val EXTRA_INTERACTION = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotificationUpdate.EXTRA_INTERACTION"
		const val EXTRA_INTERACTION_CLEAR = "EXTRA_INTERACTION_CLEAR"
		const val EXTRA_INTERACTION_ACTION = "EXTRA_INTERACTION_ACTION"
		const val EXTRA_ACTION = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotificationUpdate.EXTRA_ACTION"

	}

	val controller = NotificationUpdater(this)
	val interactionListener = IDriveNotificationInteraction(InteractionListener(), controller)
	val ranking = Ranking() // object to receive new notification rankings

	override fun onCreate() {
		super.onCreate()

		// load the emoji dictionary
		UnicodeCleaner.init(this)

		Log.i(TAG, "Registering CarNotificationInteraction listeners")
		this.registerReceiver(interactionListener, IntentFilter(INTENT_INTERACTION))
		this.registerReceiver(interactionListener, IntentFilter(INTENT_REQUEST_DATA))
	}

	override fun onDestroy() {
		super.onDestroy()
		try {
			this.unregisterReceiver(interactionListener)
		} catch (e: Exception) {}
	}

	override fun onListenerConnected() {
		super.onListenerConnected()
		UIState.notificationListenerConnected = true
		sendBroadcast(Intent(MainActivity.INTENT_REDRAW))

		updateNotificationList()
	}

	override fun onListenerDisconnected() {
		UIState.notificationListenerConnected = false
	}

	override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
		if (LOG_NOTIFICATIONS) {
			Log.i(TAG, "Notification removed: ${sbn?.notification?.extras?.get("android.title")}")
		}
		super.onNotificationRemoved(sbn, rankingMap)
		updateNotificationList()
	}

	override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
		if (!IDriveConnectionListener.isConnected) return
		val ranking = if (sbn != null && rankingMap != null) {
			rankingMap.getRanking(sbn.key, this.ranking)
			ranking
		} else null

		if (LOG_NOTIFICATIONS && sbn != null) {
			dumpNotification("Notification posted", sbn, ranking)
		}
		updateNotificationList()

		val shouldPopup = shouldPopupNotification(sbn, ranking)
		if (sbn != null && shouldPopup) {
			controller.sendNotification(sbn)
		}

		super.onNotificationPosted(sbn, rankingMap)
	}

	fun updateNotificationList() {
		if (!IDriveConnectionListener.isConnected) return
		try {
			val current = this.activeNotifications.filter {
				shouldShowNotification(it)
			}.map {
				summarizeNotification(it)
			}
			NotificationsState.replaceNotifications(current)
		} catch (e: SecurityException) {
			Log.w(TAG, "Unable to fetch activeNotifications: $e")
		}
		controller.sendNotificationList()
	}

	open class NotificationUpdater(private val context: Context) {
		/** Sends data from the phone NotificationListenerService to the car service */
		open fun sendNotificationList() {
			Log.i(TAG, "Sending notification list to the car thread")
			val intent = Intent(INTENT_UPDATE_NOTIFICATIONS)
					.setPackage(context.packageName)
			context.sendBroadcast(intent)
		}
		open fun sendNotification(notification: StatusBarNotification) {
			Log.i(TAG, "Sending new notification to the car thread")
			val intent = Intent(INTENT_NEW_NOTIFICATION)
					.setPackage(context.packageName)
					.putExtra(EXTRA_NOTIFICATION, notification.key)
			context.sendBroadcast(intent)
		}
	}

	open inner class InteractionListener {
		/** Handles interactions from the car */
		open fun cancelNotification(key: String) {
			this@NotificationListenerServiceImpl.cancelNotification(key)
		}
		open fun sendNotificationAction(key: String, action: String?) {
			try {
				val notification = this@NotificationListenerServiceImpl.activeNotifications.find { it.key == key }
				val intent = notification?.notification?.actions?.find { it.title == action }?.actionIntent
				intent?.send()
			} catch (e: SecurityException) {
				Log.w(TAG, "Unable to send action $action to notification $key: $e")
			}
		}
	}

	class IDriveNotificationInteraction(private val listener: InteractionListener, private val controller: NotificationUpdater): BroadcastReceiver() {
		/** Listens to Broadcast Intents from the car service */
		override fun onReceive(context: Context?, intent: Intent?) {
			if (intent?.action == INTENT_INTERACTION) {
				// handle a notification interaction
				val interaction = intent.getStringExtra(EXTRA_INTERACTION)
				val key = intent.getStringExtra(EXTRA_KEY)
				if (interaction == EXTRA_INTERACTION_ACTION) {
					val action = intent.getStringExtra(EXTRA_ACTION)
					Log.i(TAG, "Received request to send action to $key of type $action")
					listener.sendNotificationAction(key, action)
				} else if (interaction == EXTRA_INTERACTION_CLEAR) {
					Log.i(TAG, "Received request to clear notification $key")
					listener.cancelNotification(key)
				} else {
					Log.i(TAG, "Unknown interaction! $interaction")
				}
			} else if (intent?.action == INTENT_REQUEST_DATA) {
				// send a full list of notifications to the car
				controller.sendNotificationList()
			} else {
				Log.w(TAG, "Received unknown notification interaction: ${intent?.action}")
			}
		}
	}
}