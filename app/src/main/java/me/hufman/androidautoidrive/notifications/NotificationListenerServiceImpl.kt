package me.hufman.androidautoidrive.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.TextView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import me.hufman.androidautoidrive.CarConnectionListener
import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.notifications.CarNotificationControllerIntent.Companion.INTENT_INTERACTION
import me.hufman.androidautoidrive.notifications.NotificationParser.Companion.dumpNotification
import me.hufman.androidautoidrive.phoneui.UIState
import me.hufman.idriveconnectionkit.android.IDriveConnectionReceiver

fun Notification.isGroupSummary(): Boolean {
	val FLAG_GROUP_SUMMARY = 0x00000200     // hard-coded to work on old SDK
	return this.group != null && (this.flags and FLAG_GROUP_SUMMARY) != 0
}

class NotificationListenerServiceImpl: NotificationListenerService() {
	companion object {
		const val TAG = "IDriveNotifications"
		const val LOG_NOTIFICATIONS = false
		const val INTENT_REQUEST_DATA = "me.hufman.androidaudoidrive.PhoneNotificationUpdate.REQUEST_DATA"

		private val _serviceState = MutableLiveData(false)
		val serviceState = _serviceState as LiveData<Boolean>
	}

	val iDriveConnectionReceiver = IDriveConnectionReceiver()       // watches connection state
	val carConnectionReceiver = CarConnectionListener()             // starts MainService

	val notificationParser by lazy { NotificationParser.getInstance(this) }
	val carController = NotificationUpdaterControllerIntent(this)
	var carNotificationReceiver = CarNotificationControllerIntent.Receiver(CarNotificationControllerListener(this))
	val interactionListener = NotificationInteractionListener(carNotificationReceiver, carController)
	val broadcastReceiver = object: BroadcastReceiver() {
		// Instantiating a BroadcastReceiver in a unit test is hard, so it's factored out a bit
		override fun onReceive(p0: Context?, p1: Intent?) {
			p1 ?: return
			interactionListener.onReceive(p1)
		}
	}
	val ranking = Ranking() // object to receive new notification rankings

	override fun onCreate() {
		super.onCreate()

		// load the emoji dictionary
		UnicodeCleaner.init(this)

		// car connection status listeners
		// since we are a background service running all the time,
		// this is a handy way to listen to the car connection announcement
		// especially with Connected Classic, which doesn't have CarAPI registration
		iDriveConnectionReceiver.subscribe(this)
		carConnectionReceiver.register(this)

		// car app listeners
		Log.i(TAG, "Registering CarNotificationInteraction listeners")
		carNotificationReceiver.register(this, broadcastReceiver)
		this.registerReceiver(broadcastReceiver, IntentFilter(INTENT_REQUEST_DATA))
	}

	override fun onDestroy() {
		super.onDestroy()
		try {
			this.unregisterReceiver(broadcastReceiver)
		} catch (e: Exception) {}
		try {
			iDriveConnectionReceiver.unsubscribe(this)
		} catch (e: Exception) {}
		try {
			carConnectionReceiver.unregister(this)
		} catch (e: Exception) {}
	}

	override fun onListenerConnected() {
		super.onListenerConnected()
		_serviceState.value = true

		updateNotificationList()
	}

	override fun onListenerDisconnected() {
		_serviceState.value = false
	}

	override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
		if (LOG_NOTIFICATIONS) {
			Log.i(TAG, "Notification removed: ${sbn?.notification?.extras?.get("android.title")}")
		}
		super.onNotificationRemoved(sbn, rankingMap)
		updateNotificationList()
	}

	override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
		if (!iDriveConnectionReceiver.isConnected) return
		val ranking = if (sbn != null && rankingMap != null) {
			rankingMap.getRanking(sbn.key, this.ranking)
			ranking
		} else null

		if (LOG_NOTIFICATIONS && sbn != null) {
			dumpNotification("Notification posted", sbn, ranking)
		}
		updateNotificationList()

		val shouldPopup = notificationParser.shouldPopupNotification(sbn, ranking)
		if (sbn != null && shouldPopup) {
			carController.onNewNotification(sbn.key)
		}

		super.onNotificationPosted(sbn, rankingMap)
	}

	fun updateNotificationList() {
		if (!iDriveConnectionReceiver.isConnected) return
		try {
			val current = this.activeNotifications.filter {
				notificationParser.shouldShowNotification(it)
			}.map {
				notificationParser.summarizeNotification(it)
			}
			NotificationsState.replaceNotifications(current)
		} catch (e: SecurityException) {
			Log.w(TAG, "Unable to fetch activeNotifications: $e")
		}
		carController.onUpdatedList()
	}

	open class CarNotificationControllerListener(private val listenerService: NotificationListenerService): CarNotificationController {
		/** Handles interactions from the car app and handles them in the NotificationListenerService */
		override fun clear(key: String) {
			listenerService.cancelNotification(key)
		}
		override fun action(key: String, actionName: String) {
			try {
				val notification = listenerService.activeNotifications.find { it.key == key }
				val customViewTemplate = notification?.notification?.getContentView()
				if (customViewTemplate != null) {
					val customView = customViewTemplate.apply(listenerService, null)
					customView.collectChildren().filterIsInstance<TextView>()
						.filter { it.isClickable }
						.firstOrNull { it.text == key }?.performClick()
				} else {
					val intent = notification?.notification?.actions?.find { it.title == actionName }?.actionIntent
					intent?.send()
				}
			} catch (e: SecurityException) {
				Log.w(TAG, "Unable to send action $actionName to notification $key: $e")
			}
		}
		@SuppressLint("WrongConstant")
		override fun reply(key: String, actionName: String, reply: String) {
			try {
				val notification = listenerService.activeNotifications.find { it.key == key }
				val action = notification?.notification?.actions?.find { it.title == actionName }
				if (action != null) {
					val results = Bundle()
					action.remoteInputs.forEach {
						if (it.allowFreeFormInput) {
							results.putString(it.resultKey, reply)
						}
					}
					val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
					RemoteInput.addResultsToIntent(action.remoteInputs, intent, results)
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {   // only added in API 28
						RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
					}
					action.actionIntent.send(listenerService, 0, intent)
				}
			} catch (e: SecurityException) {
				Log.w(TAG, "Unable to send reply to $actionName to notification $key: $e")
			} catch (e: PendingIntent.CanceledException) {
				Log.w(TAG, "Unable to send reply to $actionName to notification $key: $e")
			}
		}
	}

	/**
	 * Handles incoming Intents to make requests of the NotificationListenerService
	 * For example, incoming CarNotificationController actions
	 */
	class NotificationInteractionListener(private val listenerController: CarNotificationControllerIntent.Receiver, private val carController: NotificationUpdaterController) {
		/** Handles Intents from the car app thread */
		fun onReceive(intent: Intent) {
			if (intent.action == INTENT_INTERACTION) {
				listenerController.onReceive(intent)
			} else if (intent.action == INTENT_REQUEST_DATA) {
				// send a full list of notifications to the car
				carController.onUpdatedList()
			} else {
				Log.w(TAG, "Received unknown notification interaction: ${intent.action}")
			}
		}
	}
}