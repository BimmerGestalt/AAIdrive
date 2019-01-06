package me.hufman.androidautoidrive

import android.app.Notification
import android.app.Notification.PRIORITY_LOW
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import me.hufman.androidautoidrive.carapp.notifications.CarNotificationControllerIntent
import me.hufman.androidautoidrive.carapp.notifications.NotificationListenerServiceImpl
import me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.SecurityService

class MainService: Service() {
	companion object {
		const val TAG = "MainServer"

		const val ACTION_START = "me.hufman.androidautoidrive.MainService.start"
		const val ACTION_STOP = "me.hufman.androidautoidrive.MainService.stop"
	}
	val ONGOING_NOTIFICATION_ID = 20503
	var foregroundNotification: Notification? = null

	var carappNotifications: PhoneNotifications? = null

	override fun onBind(intent: Intent?): IBinder? {
		return null
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		val action = intent?.action ?: ""
		if (action == ACTION_START) {
			handleActionStart()
		} else if (action == ACTION_STOP) {
			handleActionStop()
		}
		return Service.START_STICKY
	}

	/**
	 * Start the service
	 */
	private fun handleActionStart() {
		Log.i(TAG, "Starting up service")
		SecurityService.connect(this)
		SecurityService.subscribe(Runnable {
			combinedCallback()
		})
	}

	private fun startNotification(brand: String?) {
		Log.i(TAG, "Creating foreground notification")
		val foregroundNotificationBuilder = Notification.Builder(this)
				.setOngoing(true)
				.setContentTitle(getText(R.string.notification_title))
				.setContentText(getText(R.string.notification_description))
				.setSmallIcon(android.R.drawable.ic_menu_gallery)
				.setPriority(PRIORITY_LOW)
		if (brand == "bmw") foregroundNotificationBuilder.setContentText(getText(R.string.notification_description_bmw))
		if (brand == "mini") foregroundNotificationBuilder.setContentText(getText(R.string.notification_description_mini))
		foregroundNotification = foregroundNotificationBuilder.build()
		startForeground(ONGOING_NOTIFICATION_ID, foregroundNotification)
	}

	fun combinedCallback() {
		synchronized(MainService::class.java) {
			if (IDriveConnectionListener.isConnected && SecurityService.isConnected(IDriveConnectionListener.brand ?: "")) {
				startNotification(IDriveConnectionListener.brand)

				if (carappNotifications == null) {
					carappNotifications = PhoneNotifications(CarAppAssetManager(this, "basecoreOnlineServices"),
							PhoneAppResourcesAndroid(this),
							CarNotificationControllerIntent(this))
					carappNotifications?.onCreate(this)
					sendBroadcast(Intent(this, NotificationListenerServiceImpl.IDriveNotificationInteraction::class.java)
							.setAction(NotificationListenerServiceImpl.INTENT_REQUEST_DATA))
				} else {}
			} else {
				Log.d(TAG, "Not fully connected: IDrive:${IDriveConnectionListener.isConnected} SecurityService:${SecurityService.isConnected(IDriveConnectionListener.brand ?: "")}")
			}
		}
	}

	/**
	 * Stop the service
	 */
	private fun handleActionStop() {
		Log.i(TAG, "Shutting down service")
		synchronized(MainService::class.java) {
			stopNotification()
			carappNotifications?.onDestroy(this)
			carappNotifications = null
			SecurityService.listener = Runnable {}
		}
	}

	private fun stopNotification() {
		Log.i(TAG, "Hiding foreground notification")
		stopForeground(true)
	}

}