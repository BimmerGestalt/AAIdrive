package me.hufman.androidautoidrive.phoneui

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_notifications.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.R

class ConfigureNotificationsActivity: AppCompatActivity() {

	val appSettings = MutableAppSettingsReceiver(this)

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
		swNotificationSound.setOnCheckedChangeListener { _, isChecked ->
			appSettings[AppSettings.KEYS.NOTIFICATIONS_SOUND] = isChecked.toString()
		}
		swNotificationReadout.setOnCheckedChangeListener { buttonView, isChecked ->
			appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT] = isChecked.toString()
			redraw()
		}
		swNotificationReadoutPopup.setOnCheckedChangeListener { buttonView, isChecked ->
			appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP] = isChecked.toString()
		}
		swNotificationReadoutPopupPassenger.setOnCheckedChangeListener { buttonView, isChecked ->
			appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER] = isChecked.toString()
		}
		btnGrantSMS.setOnClickListener {
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), 20)
		}

		// spawn a Test notification
		btnTestNotification.setOnClickListener {
			createNotificationChannel()
			val actionIntent = Intent(this, CustomActionListener::class.java)
			val replyInput = RemoteInput.Builder("reply")
					.setChoices(arrayOf("Yes", "No", "\uD83D\uDC4C"))
					.build()

			val action = Notification.Action.Builder(null, "Custom Action",
					PendingIntent.getBroadcast(this, 0, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT))
					.build()
			val inputAction = Notification.Action.Builder(null, "Reply",
					PendingIntent.getBroadcast(this, 1, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT))
					.addRemoteInput(replyInput)
					.build()
			val notificationBuilder = Notification.Builder(this)
					.setSmallIcon(android.R.drawable.ic_menu_gallery)
					.setContentTitle("Test Notification")
					.setContentText("This is a test notification \ud83d\udc4d")
					.setSubText("SubText")
					.addAction(action)
					.addAction(inputAction)
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
		swNotificationSound.isChecked = appSettings[AppSettings.KEYS.NOTIFICATIONS_SOUND].toBoolean()
		swNotificationReadout.isChecked = appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT].toBoolean()
		swNotificationReadoutPopup.isChecked = appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP].toBoolean()
		paneNotificationReadout.visible = appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP].toBoolean()
		swNotificationReadoutPopupPassenger.isChecked = appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER].toBoolean()
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
			if (intent != null) {
				if (RemoteInput.getResultsFromIntent(intent) != null) {
					val reply = RemoteInput.getResultsFromIntent(intent)
					Log.i(MainActivity.TAG, "Received reply")
					Toast.makeText(context, "Reply: ${reply.getCharSequence("reply")}", Toast.LENGTH_SHORT).show()

					val manager = NotificationManagerCompat.from(context!!)
					manager.cancel(1)
				} else {
					Log.i(MainActivity.TAG, "Received custom action")
					Toast.makeText(context, "Custom Action press", Toast.LENGTH_SHORT).show()
				}
			}
		}
	}
}