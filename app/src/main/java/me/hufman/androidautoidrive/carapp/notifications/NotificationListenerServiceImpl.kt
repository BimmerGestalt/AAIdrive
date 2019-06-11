package me.hufman.androidautoidrive.carapp.notifications

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat.EXTRA_LARGE_ICON
import android.support.v4.app.NotificationCompat.EXTRA_LARGE_ICON_BIG
import android.util.Log
import me.hufman.androidautoidrive.UIState
import me.hufman.androidautoidrive.MainActivity
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
		const val INTENT_INTERACTION = "me.hufman.androidaudoidrive.PhoneNotificationUpdate.INTERACTION"
		const val INTENT_REQUEST_DATA = "me.hufman.androidaudoidrive.PhoneNotificationUpdate.REQUEST_DATA"
		const val EXTRA_KEY = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotificationUpdate.EXTRA_KEY"
		const val EXTRA_INTERACTION = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotificationUpdate.EXTRA_INTERACTION"
		const val EXTRA_INTERACTION_CLEAR = "EXTRA_INTERACTION_CLEAR"
		const val EXTRA_INTERACTION_ACTION = "EXTRA_INTERACTION_ACTION"
		const val EXTRA_ACTION = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotificationUpdate.EXTRA_ACTION"

		/**
		 * Summarize an Android Notification into what should be shown in the car
		 */
		fun summarizeNotification(sbn: StatusBarNotification): CarNotification {
			var title:String? = null
			var text:String? = null
			var summary:String? = null
			val extras = sbn.notification.extras
			var icon = sbn.notification.smallIcon

			// some extra handling for special notifications
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
					extras.getString(Notification.EXTRA_TEMPLATE) == "android.app.Notification\$MessagingStyle") {
				return summarizeMessagingNotification(sbn)
			}

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
			if (extras.getParcelable<Parcelable>(EXTRA_LARGE_ICON) != null) {
				// might have a user avatar, which might be an icon or a bitmap
				val parcel: Parcelable = extras.getParcelable(EXTRA_LARGE_ICON)
				if (parcel is Icon) icon = parcel
				if (parcel is Bitmap) icon = Icon.createWithBitmap(parcel)
			}
			if (extras.getParcelable<Parcelable>(EXTRA_LARGE_ICON_BIG) != null) {
				// might have a user avatar, which might be an icon or a bitmap
				val parcel: Parcelable = extras.getParcelable(EXTRA_LARGE_ICON_BIG)
				if (parcel is Icon) icon = parcel
				if (parcel is Bitmap) icon = Icon.createWithBitmap(parcel)
			}

			val summarized = CarNotification(sbn.packageName, sbn.key, icon, sbn.isClearable, sbn.notification.actions ?: arrayOf(),
					title, summary, text)
			return summarized
		}

		@RequiresApi(Build.VERSION_CODES.O)
		fun summarizeMessagingNotification(sbn: StatusBarNotification): CarNotification {
			val extras = sbn.notification.extras
			val title = extras.getCharSequence(Notification.EXTRA_TITLE).toString()
			val historicMessages = extras.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES) ?: arrayOf()
			val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: arrayOf()
			val summary = extras.getCharSequence(Notification.EXTRA_TEXT).toString()
			val text = (historicMessages + messages).filterIsInstance<Bundle>().takeLast(5).joinToString("\n") {
				"${it.getCharSequence("sender")}: ${it.getCharSequence("text")}"
			}

			var icon = sbn.notification.smallIcon
			if (extras.getParcelable<Parcelable>(EXTRA_LARGE_ICON) != null) {
				// might have a user avatar, which might be an icon or a bitmap
				val parcel: Parcelable = extras.getParcelable(EXTRA_LARGE_ICON)
				if (parcel is Icon) icon = parcel
				if (parcel is Bitmap) icon = Icon.createWithBitmap(parcel)
			}
			if (extras.getParcelable<Parcelable>(EXTRA_LARGE_ICON_BIG) != null) {
				// might have a user avatar, which might be an icon or a bitmap
				val parcel: Parcelable = extras.getParcelable(EXTRA_LARGE_ICON_BIG)
				if (parcel is Icon) icon = parcel
				if (parcel is Bitmap) icon = Icon.createWithBitmap(parcel)
			}
			return CarNotification(sbn.packageName, sbn.key, icon, sbn.isClearable, sbn.notification.actions ?: arrayOf(),
					title, summary, text)
		}

		fun shouldPopupNotification(sbn: StatusBarNotification?): Boolean {
			if (sbn == null) return false
			val alreadyShown = NotificationsState.notifications.any {
				it.key == sbn.key && it.text == summarizeNotification(sbn).text
			}
			return sbn.isClearable && !alreadyShown
		}

		fun shouldShowNotification(sbn: StatusBarNotification): Boolean {
			return !sbn.notification.isGroupSummary() &&
			       sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT) != null &&
			       (sbn.isClearable || sbn.notification.actions?.isNotEmpty() == true)
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
		Log.i(TAG, "Notification posted: ${extras?.get("android.title")} with the keys:\n$details")
		super.onNotificationPosted(sbn, rankingMap)
		val shouldPopup = shouldPopupNotification(sbn)
		updateNotificationList()
		if (sbn != null && shouldPopup) controller.sendNotification(sbn)
	}

	fun updateNotificationList() {
		synchronized(NotificationsState.notifications) {
			NotificationsState.notifications.clear()
			NotificationsState.notifications.addAll(this.activeNotifications.filter {
				shouldShowNotification(it)
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