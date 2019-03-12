package me.hufman.androidautoidrive.carapp.notifications

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import me.hufman.androidautoidrive.UIState
import me.hufman.androidautoidrive.MainActivity
import me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications.Companion.EXTRA_NOTIFICATION
import me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications.Companion.INTENT_NEW_NOTIFICATION
import me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications.Companion.INTENT_UPDATE_NOTIFICATIONS
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener

class NotificationListenerServiceImpl: NotificationListenerService() {
	companion object {
		const val TAG = "IDriveNotifications"
		const val INTENT_INTERACTION = "me.hufman.androidaudoidrive.PhoneNotificationUpdate.INTERACTION"
		const val INTENT_REQUEST_DATA = "me.hufman.androidaudoidrive.PhoneNotificationUpdate.REQUEST_DATA"
		const val EXTRA_KEY = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotificationUpdate.EXTRA_KEY"
		const val EXTRA_INTERACTION = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotificationUpdate.EXTRA_INTERACTION"
		const val EXTRA_INTERACTION_CLEAR = "EXTRA_INTERACTION_CLEAR"
		const val EXTRA_INTERACTION_ACTION = "EXTRA_INTERACTION_ACTION"
		const val EXTRA_ACTION = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotificationUpdate.EXTRA_ACTION"

		fun summarizeNotification(sbn: StatusBarNotification): CarNotification {
			var title:String? = null
			var text:String? = null
			var summary:String? = null
			val extras = sbn.notification.extras
			if (extras.getCharSequence(Notification.EXTRA_TITLE) != null) {
				title = extras.getCharSequence(Notification.EXTRA_TITLE).toString()
			}
			if (extras.getCharSequence(Notification.EXTRA_TEXT) != null) {
				text = extras.getCharSequence(Notification.EXTRA_TEXT).toString()
			}
			// full expanded view, like an email body
			if (extras.getCharSequence(Notification.EXTRA_TITLE_BIG) != null) {
				title = extras.getCharSequence(Notification.EXTRA_TITLE_BIG).toString()
			}
			if (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) != null) {
				text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
			}
			if (extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT) != null) {
				summary = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT).toString()
			}
			if (extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) != null) {
				text = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).joinToString("\n")
			}
			val summarized = CarNotification(sbn.packageName, sbn.key, sbn.notification.smallIcon, sbn.isClearable, sbn.notification.actions ?: arrayOf(),
					title, summary, text)
			return summarized
		}
	}

	val controller = NotificationUpdater(this)
	val interactionListener = IDriveNotificationInteraction(InteractionListener(), controller)

	override fun onCreate() {
		super.onCreate()
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
		val notifications = this.activeNotifications
		val notificationStrings = notifications.mapNotNull { it.notification.tickerText }.joinToString()
		Log.i(TAG, "Notifications already showing: $notificationStrings")
		updateNotificationList()
	}

	override fun onListenerDisconnected() {
		UIState.notificationListenerConnected = false
	}

	override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
		if (!IDriveConnectionListener.isConnected) return
		Log.i(TAG, "Notification removed: ${sbn?.notification?.extras?.get("android.title")}")
		super.onNotificationRemoved(sbn, rankingMap)
		updateNotificationList()
	}

	override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
		if (!IDriveConnectionListener.isConnected) return
		val extras = sbn?.notification?.extras
		val details = extras?.keySet()?.map { "  ${it}=>${extras.get(it)}" }?.joinToString("\n") ?: ""
		Log.i(TAG, "Notification posted: ${extras?.get("android.title")} with the ticker text ${sbn?.notification?.tickerText} and the keys:\n$details")
		super.onNotificationPosted(sbn, rankingMap)
		val alreadyShown = NotificationsState.notifications.any {
			it.key == sbn?.key
		}
		updateNotificationList()
		if (sbn != null && sbn.isClearable && !alreadyShown) controller.sendNotification(sbn)
	}

	fun updateNotificationList() {
		synchronized(NotificationsState.notifications) {
			NotificationsState.notifications.clear()
			NotificationsState.notifications.addAll(this.activeNotifications.filter {
				it.isClearable || it.notification.actions?.isNotEmpty() == true
			}.map {
				summarizeNotification(it)
			})
		}
		controller.sendNotificationList()
	}

	open class NotificationUpdater(private val context: Context) {
		/** Sends data from the phone NotificationListenerService to the car service */
		open fun sendNotificationList() {
			if (!IDriveConnectionListener.isConnected) return
			Log.i(TAG, "Sending notification list to the car thread")
			val intent = Intent(INTENT_UPDATE_NOTIFICATIONS)
					.setPackage(context.packageName)
			context.sendBroadcast(intent)
		}
		open fun sendNotification(notification: StatusBarNotification) {
			if (!IDriveConnectionListener.isConnected) return
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
			val notification = this@NotificationListenerServiceImpl.activeNotifications.find { it.key == key }
			val intent = notification?.notification?.actions?.find { it.title == action }?.actionIntent
			if (intent != null) {
				intent.send()
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