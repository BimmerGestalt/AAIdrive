package me.hufman.androidautoidrive.phoneui

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_notifications.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettings
import me.hufman.androidautoidrive.R

class ConfigureNotificationsActivity: AppCompatActivity() {

	val appSettings = MutableAppSettings(this)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_notifications)

		swNotificationPopup.setOnCheckedChangeListener { buttonView, isChecked ->
			appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP] = isChecked.toString()
			redraw()
		}
		swNotificationPopupPassenger.setOnCheckedChangeListener { buttonView, isChecked ->
			appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER] = isChecked.toString()
		}
		btnGrantSMS.setOnClickListener {
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), 20)
		}

		// spawn a Test notification
		btnTestNotification.setOnClickListener {
			createNotificationChannel()
			val actionIntent = Intent(this, CustomActionListener::class.java)

			val action = Notification.Action.Builder(null, "Custom action test",
					PendingIntent.getBroadcast(this, 0, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT))
					.build()
			val notificationBuilder = Notification.Builder(this)
					.setSmallIcon(android.R.drawable.ic_menu_gallery)
					.setContentTitle("Test Notification")
					.setContentText("This is a test notification \ud83d\udc4d")
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
		appSettings.callback = { redraw() }
	}

	override fun onPause() {
		super.onPause()
		appSettings.callback = null
	}

	fun hasSMSPermission(): Boolean {
		return (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
	}

	fun redraw() {
		swNotificationPopup.isChecked = appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP].toBoolean()
		paneNotificationPopup.visible = appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP].toBoolean()
		swNotificationPopupPassenger.isChecked = appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER].toBoolean()
		paneSMSPermission.visible = !hasSMSPermission()
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