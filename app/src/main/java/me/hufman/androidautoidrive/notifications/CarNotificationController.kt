package me.hufman.androidautoidrive.notifications

import android.content.Context
import android.content.Intent
import android.util.Log
import me.hufman.androidautoidrive.carapp.notifications.TAG
import me.hufman.androidautoidrive.notifications.NotificationListenerServiceImpl.Companion.INTENT_INTERACTION


interface CarNotificationController {
	/**
	 * When the user selects a thing in the car, this Controller reacts and updates the phone
	 */
	fun clear(notification: CarNotification)
	fun action(notification: CarNotification, action: CarNotification.Action)
	fun reply(notification: CarNotification, action: CarNotification.Action, reply: String)
}

class CarNotificationControllerIntent(val context: Context): CarNotificationController {
	override fun clear(notification: CarNotification) {
		Log.i(TAG, "Sending request to clear ${notification.key}")
		context.sendBroadcast(Intent(INTENT_INTERACTION)
				.setPackage(context.packageName)
				.putExtra(NotificationListenerServiceImpl.EXTRA_INTERACTION, NotificationListenerServiceImpl.EXTRA_INTERACTION_CLEAR)
				.putExtra(NotificationListenerServiceImpl.EXTRA_KEY, notification.key)
		)
	}

	override fun action(notification: CarNotification, action: CarNotification.Action) {
		Log.i(TAG, "Sending request to custom action ${notification.key}:${action.name}")
		context.sendBroadcast(Intent(INTENT_INTERACTION)
				.setPackage(context.packageName)
				.putExtra(NotificationListenerServiceImpl.EXTRA_INTERACTION, NotificationListenerServiceImpl.EXTRA_INTERACTION_ACTION)
				.putExtra(NotificationListenerServiceImpl.EXTRA_KEY, notification.key)
				.putExtra(NotificationListenerServiceImpl.EXTRA_ACTION, action.name)
		)
	}

	override fun reply(notification: CarNotification, action: CarNotification.Action, reply: String) {
		Log.i(TAG, "Sending reply to custom action ${notification.key}:${action.name}")
		context.sendBroadcast(Intent(INTENT_INTERACTION)
				.setPackage(context.packageName)
				.putExtra(NotificationListenerServiceImpl.EXTRA_INTERACTION, NotificationListenerServiceImpl.EXTRA_INTERACTION_REPLY)
				.putExtra(NotificationListenerServiceImpl.EXTRA_KEY, notification.key)
				.putExtra(NotificationListenerServiceImpl.EXTRA_ACTION, action.name)
				.putExtra(NotificationListenerServiceImpl.EXTRA_REPLY, reply)
		)
	}
}
