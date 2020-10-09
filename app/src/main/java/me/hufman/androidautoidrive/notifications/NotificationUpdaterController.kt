package me.hufman.androidautoidrive.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log

interface NotificationUpdaterController {
	/** Notify that the list has changed */
	fun onUpdatedList()
	/** Notify that this notification is new */
	fun onNewNotification(key: String)
}

/** Sends data from the phone NotificationListenerService to the car service */
class NotificationUpdaterControllerIntent(val context: Context): NotificationUpdaterController {
	companion object {
		const val INTENT_UPDATE_NOTIFICATIONS = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications.UPDATE_NOTIFICATIONS"
		const val INTENT_NEW_NOTIFICATION = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications.NEW_NOTIFICATION"
		private const val EXTRA_NOTIFICATION = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications.EXTRA_NOTIFICATION"
	}

	override fun onUpdatedList() {
		Log.i(NotificationListenerServiceImpl.TAG, "Sending notification list to the car thread")
		val intent = Intent(INTENT_UPDATE_NOTIFICATIONS)
				.setPackage(context.packageName)
		context.sendBroadcast(intent)
	}
	override fun onNewNotification(key: String) {
		Log.i(NotificationListenerServiceImpl.TAG, "Sending new notification to the car thread")
		val intent = Intent(INTENT_NEW_NOTIFICATION)
				.setPackage(context.packageName)
				.putExtra(EXTRA_NOTIFICATION, key)
		context.sendBroadcast(intent)
	}

	class Receiver(private val receiver: NotificationUpdaterController) {
		fun register(context: Context, broadcastReceiver: BroadcastReceiver, handler: Handler?) {
			context.registerReceiver(broadcastReceiver, IntentFilter(INTENT_UPDATE_NOTIFICATIONS), null, handler)
			context.registerReceiver(broadcastReceiver, IntentFilter(INTENT_NEW_NOTIFICATION), null, handler)
		}

		fun onReceive(intent: Intent) {
			if (intent.action == INTENT_NEW_NOTIFICATION) {
				val notificationKey = intent.getStringExtra(EXTRA_NOTIFICATION) ?: return
				receiver.onNewNotification(notificationKey)
			}
			if (intent.action == INTENT_UPDATE_NOTIFICATIONS) {
				receiver.onUpdatedList()
			}
		}
	}
}