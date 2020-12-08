package me.hufman.androidautoidrive.phoneui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import java.lang.IllegalStateException
import kotlinx.android.synthetic.main.activity_main.*
import me.hufman.androidautoidrive.*
import me.hufman.idriveconnectionkit.android.IDriveConnectionObserver
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import java.util.*

class MainActivity : AppCompatActivity() {

	companion object {
		const val INTENT_REDRAW = "me.hufman.androidautoidrive.REDRAW"
		const val TAG = "MainActivity"
		const val NOTIFICATION_SERVICE_TIMEOUT = 1000
		const val SECURITY_SERVICE_TIMEOUT = 1000
		const val REDRAW_INTERVAL = 5000L
		const val REQUEST_LOCATION = 4000
	}
	val handler = Handler()
	val appSettings by lazy { MutableAppSettingsReceiver(this) }
	val idriveConnectionObserver = IDriveConnectionObserver()
	val carInformationObserver = CarInformationObserver()
	val redrawListener = RedrawListener()
	val redrawTask = RedrawTask()

	var whenActivityStarted = 0L

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Analytics.init(this)
		AppSettings.loadSettings(this)
		L.loadResources(this)

		setContentView(R.layout.activity_main)

		idriveConnectionObserver.callback = { runOnUiThread { redraw() } }
		carInformationObserver.callback = { runOnUiThread { redraw() }}
		swMessageNotifications.setOnCheckedChangeListener { buttonView, isChecked ->
			if (buttonView != null) onChangedSwitchNotifications(isChecked)
			redraw()
		}
		btnConfigureNotifications.setOnClickListener {
			val intent = Intent(this, ConfigureNotificationsActivity::class.java)
			startActivity(intent)
		}
		swGMaps.setOnCheckedChangeListener { buttonView, isChecked ->
			if (buttonView != null) onChangedSwitchGMaps(isChecked)
			redraw()
		}
		btnConfigureMusic.setOnClickListener {
			val intent = Intent(this, MusicActivity::class.java)
			startActivity(intent)
		}
		btnHelp.setOnClickListener {
			val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://hufman.github.io/AndroidAutoIdrive/faq.html"))
			startActivity(intent)
		}

		txtConnectionStatus.setOnClickListener {
			val intent = Intent(this, SetupActivity::class.java)
			startActivity(intent)
		}

		redrawTask.schedule()
		registerReceiver(redrawListener, IntentFilter(INTENT_REDRAW))
	}

	override fun onDestroy() {
		super.onDestroy()
		unregisterReceiver(redrawListener)
	}

	fun onChangedSwitchNotifications(isChecked: Boolean) {
		appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS] = isChecked.toString()
		if (isChecked) {
			// make sure we have permissions to read the notifications
			val ageOfActivity = System.currentTimeMillis() - whenActivityStarted
			if (ageOfActivity > SECURITY_SERVICE_TIMEOUT && (!hasNotificationPermission() || !UIState.notificationListenerConnected)) {
				promptNotificationPermission()
			}
		}
	}

	fun promptNotificationPermission() {
		startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
	}

	fun hasNotificationPermission(): Boolean {
		return UIState.notificationListenerConnected && NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
	}

	fun onChangedSwitchGMaps(isChecked: Boolean) {
		appSettings[AppSettings.KEYS.ENABLED_GMAPS] = isChecked.toString()
		if (isChecked) {
			// make sure we have permissions to show current location
			if (!hasLocationPermission()) {
				promptForLocation()
			}
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

		whenActivityStarted = System.currentTimeMillis()

		redraw()

		// try starting the service, to try connecting to the car with current app settings
		// changes to settings are picked up automatically by the service
		startMainService()
	}

	fun redraw() {
		if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
			return
		}

		val ageOfActivity = System.currentTimeMillis() - whenActivityStarted

		// reset the Notification setting to false if we don't have permission
		// wait a bit to make sure the Notification Listener actually is running
		// sometimes when restarting, the listener takes a bit to start up
		if (ageOfActivity > NOTIFICATION_SERVICE_TIMEOUT && !hasNotificationPermission()) {
			appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS] = "false"
		}
		// reset the GMaps setting if we don't have permission
		if (!hasLocationPermission()) {
			appSettings[AppSettings.KEYS.ENABLED_GMAPS] = "false"
		}

		swMessageNotifications.isChecked = appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS].toBoolean()
		paneNotifications.visible = appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS].toBoolean()
		swGMaps.isChecked = appSettings[AppSettings.KEYS.ENABLED_GMAPS].toBoolean()
		paneGMaps.visible = appSettings[AppSettings.KEYS.ENABLED_GMAPS].toBoolean()

		if (ageOfActivity > SECURITY_SERVICE_TIMEOUT && !SecurityAccess.getInstance(this).isConnected()) {
			txtConnectionStatus.text = resources.getString(R.string.connectionStatusMissingConnectedApp)
			txtConnectionStatus.setBackgroundColor(resources.getColor(R.color.connectionError, null))
		} else if (!idriveConnectionObserver.isConnected) {
			txtConnectionStatus.text = resources.getString(R.string.connectionStatusWaiting)
			txtConnectionStatus.setBackgroundColor(resources.getColor(R.color.connectionWaiting, null))
		} else {
			val chassisCode = ChassisCode.fromCode(carInformationObserver.capabilities["vehicle.type"] ?: "Unknown")
			txtConnectionStatus.text = if (chassisCode != null) {
				resources.getString(R.string.notification_description_chassiscode, chassisCode.toString())
			} else {
				when (idriveConnectionObserver.brand?.toLowerCase(Locale.ROOT)) {
					"bmw" -> resources.getString(R.string.notification_description_bmw)
					"mini" -> resources.getString(R.string.notification_description_mini)
					else -> resources.getString(R.string.notification_description)
				}
			}
			txtConnectionStatus.setBackgroundColor(resources.getColor(R.color.connectionConnected, null))
		}
	}

	fun startMainService() {
		/** Start the service after enabling an app */
		try {
			this.startService(Intent(this, MainService::class.java).setAction(MainService.ACTION_START))
		} catch (e: IllegalStateException) {
			// Android Oreo strenuously objects to starting the service if the activity isn't visible
			// for example, when Android Studio tries to start the Activity with the screen off
		}
	}

	inner class RedrawTask: Runnable {
		fun schedule(delay:Long = REDRAW_INTERVAL) {
			handler.removeCallbacks(this)
			handler.postDelayed(this, delay)
		}
		override fun run() {
			redraw()
			schedule()
		}
	}

	inner class RedrawListener: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			runOnUiThread { redraw() }
		}
	}

}
