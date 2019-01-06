package me.hufman.androidautoidrive.carapp.notifications

import android.content.Context
import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import android.util.Log


interface CarNotificationController {
	/**
	 * When the user selects a thing in the car, this Controller reacts and updates the phone
	 */
	fun clear(sbn: CarNotification)
	fun action(sbn: CarNotification, actionTitle: String)
}

class CarNotificationControllerIntent(val context: Context): CarNotificationController {
	override fun clear(notification: CarNotification) {
		Log.i(TAG, "Sending request to clear ${notification.key}")
		LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(context.applicationContext, NotificationListenerServiceImpl.IDriveNotificationInteraction::class.java)
				.setAction(NotificationListenerServiceImpl.INTENT_INTERACTION)
				.putExtra(NotificationListenerServiceImpl.EXTRA_INTERACTION, NotificationListenerServiceImpl.EXTRA_INTERACTION_CLEAR)
				.putExtra(NotificationListenerServiceImpl.EXTRA_KEY, notification.key)
		)
	}

	override fun action(notification: CarNotification, actionTitle: String) {
		Log.i(TAG, "Sending request to custom action ${notification.key}:${actionTitle}")
		LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(context.applicationContext, NotificationListenerServiceImpl.IDriveNotificationInteraction::class.java)
				.setAction(NotificationListenerServiceImpl.INTENT_INTERACTION)
				.putExtra(NotificationListenerServiceImpl.EXTRA_INTERACTION, NotificationListenerServiceImpl.EXTRA_INTERACTION_ACTION)
				.putExtra(NotificationListenerServiceImpl.EXTRA_KEY, notification.key)
				.putExtra(NotificationListenerServiceImpl.EXTRA_ACTION, actionTitle)
		)
	}

}
