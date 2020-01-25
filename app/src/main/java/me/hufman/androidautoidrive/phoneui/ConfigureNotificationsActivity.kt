package me.hufman.androidautoidrive.phoneui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v4.app.NotificationManagerCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_notifications.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.R

class ConfigureNotificationsActivity: AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_notifications)

		swNotificationPopup.setOnCheckedChangeListener { buttonView, isChecked ->
			AppSettings.saveSetting(this, AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP, isChecked.toString())
			redraw()
		}
		swNotificationPopupPassenger.setOnCheckedChangeListener { buttonView, isChecked ->
			AppSettings.saveSetting(this, AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER, isChecked.toString())
		}

		// spawn a Test notification
		createNotificationChannel()
		btnTestNotification.setOnClickListener {
			val actionIntent = Intent(this, CustomActionListener::class.java)

			val action = Notification.Action.Builder(null, "Custom action test",
					PendingIntent.getBroadcast(this, 0, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT))
					.build()
			val notificationBuilder = Notification.Builder(this)
					.setSmallIcon(android.R.drawable.ic_menu_gallery)
					.setContentTitle("Test Notification")
					.setContentText("This is a test notification")
					.setSubText("SubText")
					.addAction(action)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				notificationBuilder.setChannelId(MainActivity.NOTIFICATION_CHANNEL_ID)
			}
			val notification = notificationBuilder.build()
			val manager = NotificationManagerCompat.from(this)
			manager.notify(1, notification)
		}

	}

	override fun onResume() {
		super.onResume()

		redraw()
	}

	fun redraw() {
		swNotificationPopup.isChecked = AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP].toBoolean()
		paneNotificationPopup.visible = AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP].toBoolean()
		swNotificationPopupPassenger.isChecked = AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER].toBoolean()
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(MainActivity.NOTIFICATION_CHANNEL_ID,
					MainActivity.NOTIFICATION_CHANNEL_NAME,
					NotificationManager.IMPORTANCE_DEFAULT)

			val notificationManager = getSystemService(NotificationManager::class.java)
			notificationManager.createNotificationChannel(channel)
		}
	}

	class CustomActionListener: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			Log.i(MainActivity.TAG, "Received custom action")
			Toast.makeText(context, "Custom Action press", Toast.LENGTH_SHORT).show()
		}
	}
}