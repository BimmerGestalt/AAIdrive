package me.hufman.androidautoidrive.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import me.hufman.androidautoidrive.carapp.notifications.TAG


interface CarNotificationController {
	/**
	 * When the user selects a thing in the car, this Controller reacts and updates the phone
	 */
	fun clear(key: String)
	fun action(key: String, actionName: String)
	fun reply(key: String, actionName: String, reply: String)
}

/**
 * Represents user actions against a notification
 *
 * Mainly, interactions from the car app portion to the NotificationListenerService to handle
 */
class CarNotificationControllerIntent(private val context: Context): CarNotificationController {
	companion object {
		const val INTENT_INTERACTION = "me.hufman.androidaudoidrive.PhoneNotificationUpdate.INTERACTION"
		private const val EXTRA_KEY = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotificationUpdate.EXTRA_KEY"
		private const val EXTRA_INTERACTION = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotificationUpdate.EXTRA_INTERACTION"
		private const val EXTRA_INTERACTION_CLEAR = "EXTRA_INTERACTION_CLEAR"
		private const val EXTRA_INTERACTION_ACTION = "EXTRA_INTERACTION_ACTION"
		private const val EXTRA_INTERACTION_REPLY = "EXTRA_INTERACTION_REPLY"
		private const val EXTRA_ACTION = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotificationUpdate.EXTRA_ACTION"
		private const val EXTRA_REPLY = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotificationUpdate.EXTRA_REPLY"
	}

	override fun clear(key: String) {
		Log.i(TAG, "Sending request to clear $key")
		context.sendBroadcast(Intent(INTENT_INTERACTION)
				.setPackage(context.packageName)
				.putExtra(EXTRA_INTERACTION, EXTRA_INTERACTION_CLEAR)
				.putExtra(EXTRA_KEY, key)
		)
	}

	override fun action(key: String, actionName: String) {
		Log.i(TAG, "Sending request to custom action ${key}:${actionName}")
		context.sendBroadcast(Intent(INTENT_INTERACTION)
				.setPackage(context.packageName)
				.putExtra(EXTRA_INTERACTION, EXTRA_INTERACTION_ACTION)
				.putExtra(EXTRA_KEY, key)
				.putExtra(EXTRA_ACTION, actionName)
		)
	}

	override fun reply(key: String, actionName: String, reply: String) {
		Log.i(TAG, "Sending reply to custom action ${key}:${actionName}")
		context.sendBroadcast(Intent(INTENT_INTERACTION)
				.setPackage(context.packageName)
				.putExtra(EXTRA_INTERACTION, EXTRA_INTERACTION_REPLY)
				.putExtra(EXTRA_KEY, key)
				.putExtra(EXTRA_ACTION, actionName)
				.putExtra(EXTRA_REPLY, reply)
		)
	}

	class Receiver(val receiver: CarNotificationController) {
		fun register(context: Context, broadcastReceiver: BroadcastReceiver) {
			context.registerReceiver(broadcastReceiver, IntentFilter(INTENT_INTERACTION))
		}

		fun onReceive(intent: Intent) {
			// handle a notification interaction
			val interaction = intent.getStringExtra(EXTRA_INTERACTION)
			val key = intent.getStringExtra(EXTRA_KEY) ?: return
			if (interaction == EXTRA_INTERACTION_ACTION) {
				val action = intent.getStringExtra(EXTRA_ACTION) ?: return
				Log.i(TAG, "Received request to send action to $key of type $action")
				receiver.action(key, action)
			} else if (interaction == EXTRA_INTERACTION_CLEAR) {
				Log.i(TAG, "Received request to clear notification $key")
				receiver.clear(key)
			} else if (interaction == EXTRA_INTERACTION_REPLY) {
				val action = intent.getStringExtra(EXTRA_ACTION) ?: return
				val reply = intent.getStringExtra(EXTRA_REPLY) ?: return
				Log.i(TAG, "Received request to reply to $key of action $action with reply $reply")
				receiver.reply(key, action, reply)
			} else {
				Log.i(TAG, "Unknown interaction! $interaction")
			}
		}
	}
}
