package me.hufman.androidautoidrive

import android.app.Notification
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.widget.CompoundButton
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import me.hufman.idriveconnectionkit.android.SecurityService

class MainActivity : AppCompatActivity() {

	companion object {
		const val INTENT_REDRAW = "me.hufman.androidautoidrive.REDRAW"
		const val TAG = "MainActivity"
		const val REQUEST_LOCATION = 4000
	}
	val redrawListener = RedrawListener()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		AppSettings.loadSettings(this)
		setContentView(R.layout.activity_main)

		swMessageNotifications.setOnCheckedChangeListener { buttonView, isChecked ->
			if (buttonView != null) onChangedSwitchNotifications(buttonView, isChecked)
		}
		swNotificationPopup.setOnCheckedChangeListener { buttonView, isChecked ->
			AppSettings.saveSetting(this, AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP, isChecked.toString())
		}
		swNotificationPopupPassenger.setOnCheckedChangeListener { buttonView, isChecked ->
			AppSettings.saveSetting(this, AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER, isChecked.toString())
		}
		swGMaps.setOnCheckedChangeListener { buttonView, isChecked ->
			if (buttonView != null) onChangedSwitchGMaps(buttonView, isChecked)
		}

		// spawn a Test notification
		btnTestNotification.setOnClickListener {
			//val actionIntent = Intent(this, CustomActionListener::class.java)
			val actionIntent = Intent(this, CustomActionListener::class.java)

			val action = Notification.Action.Builder(null, "Custom action test",
					PendingIntent.getBroadcast(this, 0, actionIntent, FLAG_UPDATE_CURRENT ))
					.build()
			val notification = Notification.Builder(this)
					.setSmallIcon(android.R.drawable.ic_menu_gallery)
					.setContentTitle("Test Notification")
					.setContentText("Text")
					.setSubText("SubText")
					.addAction(action)
					.build()
			val manager = NotificationManagerCompat.from(this)
			manager.notify(1, notification)
		}

		registerReceiver(redrawListener, IntentFilter(INTENT_REDRAW))
	}

	fun onChangedSwitchNotifications(buttonView: CompoundButton, isChecked: Boolean) {
		AppSettings.saveSetting(this, AppSettings.KEYS.ENABLED_NOTIFICATIONS, isChecked.toString())
		if (isChecked) {
			// make sure we have permissions to read the notifications
			if (!hasNotificationPermission()) {
				startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
			} else {
				startMainService()
			}
		} else {
			startMainService()
		}
	}

	fun hasNotificationPermission(): Boolean {
		return (Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
				|| UIState.notificationListenerConnected)
	}

	fun onChangedSwitchGMaps(buttonView: CompoundButton, isChecked: Boolean) {
		AppSettings.saveSetting(this, AppSettings.KEYS.ENABLED_GMAPS, isChecked.toString())
		if (isChecked) {
			// make sure we have permissions to show current location
			if (!hasLocationPermission()) {
				promptForLocation()
			} else {
				startMainService()
			}
		} else {
			startMainService()
		}
	}

	fun hasLocationPermission(): Boolean {
		return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
	}
	fun promptForLocation() {
		ActivityCompat.requestPermissions(this,
				arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
				REQUEST_LOCATION)
	}

	override fun onResume() {
		super.onResume()

		// reset the Notification setting to false if we don't have permission
		if (!hasNotificationPermission()) {
			AppSettings.saveSetting(this, AppSettings.KEYS.ENABLED_NOTIFICATIONS, "false")
		}
		// reset the GMaps setting if we don't have permission
		if (!hasLocationPermission()) {
			AppSettings.saveSetting(this, AppSettings.KEYS.ENABLED_GMAPS, "false")
		}
		redraw()

		// try starting the service, to try connecting to the car with current app settings
		// for example, after we resume from enabling the notification
		startMainService()
	}

	fun redraw() {
		swMessageNotifications.isChecked = AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS].toBoolean() &&
				UIState.notificationListenerConnected
		swNotificationPopup.isChecked = AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP].toBoolean()
		swNotificationPopupPassenger.isChecked = AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER].toBoolean()
		swGMaps.isChecked = AppSettings[AppSettings.KEYS.ENABLED_GMAPS].toBoolean()
	}

	fun startMainService() {
		/** Start the service after enabling an app */
		this.startService(Intent(this, MainService::class.java).setAction(MainService.ACTION_START))
	}

	inner class RedrawListener: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			runOnUiThread { redraw() }
		}
	}

	class CustomActionListener: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			Log.i(TAG, "Received custom action")
			Toast.makeText(context, "Custom Action press", Toast.LENGTH_SHORT).show()
		}
	}
}
