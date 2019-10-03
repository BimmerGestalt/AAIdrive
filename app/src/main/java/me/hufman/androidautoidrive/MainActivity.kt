package me.hufman.androidautoidrive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.*
import kotlinx.android.synthetic.main.activity_main.*
import me.hufman.androidautoidrive.music.MusicAppDiscovery
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.SecurityService
import java.lang.IllegalStateException
import kotlin.math.max

class MainActivity : AppCompatActivity() {

	companion object {
		const val INTENT_REDRAW = "me.hufman.androidautoidrive.REDRAW"
		const val TAG = "MainActivity"
		const val SECURITY_SERVICE_TIMEOUT = 1000
		const val REDRAW_INTERVAL = 5000L
		const val REQUEST_LOCATION = 4000

		const val NOTIFICATION_CHANNEL_ID = "TestNotification"
		const val NOTIFICATION_CHANNEL_NAME = "Test Notification"
	}
	val handler = Handler()
	val redrawListener = RedrawListener()
	val redrawTask = RedrawTask()
	val displayedApps = ArrayList<MusicAppInfo>()
	val appDiscoveryThread = AppDiscoveryThread(this) { apps ->
		handler.post {
			displayedApps.clear()
			displayedApps.addAll(apps)
			listMusicApps.invalidateViews() // redraw the app list
		}
	}
	var whenActivityStarted = 0L

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Analytics.init(this)
		AppSettings.loadSettings(this)
		L.loadResources(this)

		setContentView(R.layout.activity_main)

		swMessageNotifications.setOnCheckedChangeListener { buttonView, isChecked ->
			if (buttonView != null) onChangedSwitchNotifications(buttonView, isChecked)
			redraw()
		}
		swNotificationPopup.setOnCheckedChangeListener { buttonView, isChecked ->
			AppSettings.saveSetting(this, AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP, isChecked.toString())
			redraw()
		}
		swNotificationPopupPassenger.setOnCheckedChangeListener { buttonView, isChecked ->
			AppSettings.saveSetting(this, AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER, isChecked.toString())
		}
		swGMaps.setOnCheckedChangeListener { buttonView, isChecked ->
			if (buttonView != null) onChangedSwitchGMaps(buttonView, isChecked)
			redraw()
		}
		swGmapSyle.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
			override fun onNothingSelected(parent: AdapterView<*>?) {
			}

			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				val value = parent?.getItemAtPosition(position) ?: return
				Log.i(TAG, "Setting gmaps style to $value")
				AppSettings.saveSetting(this@MainActivity, AppSettings.KEYS.GMAPS_STYLE, value.toString().toLowerCase().replace(' ', '_'))
				sendBroadcast(Intent(INTENT_GMAP_RELOAD_SETTINGS))
			}
		}
		swGmapWidescreen.setOnCheckedChangeListener { buttonView, isChecked ->
			AppSettings.saveSetting(this, AppSettings.KEYS.MAP_WIDESCREEN, isChecked.toString())
		}
		swAudioContext.setOnCheckedChangeListener { buttonView, isChecked ->
			AppSettings.saveSetting(this, AppSettings.KEYS.AUDIO_ENABLE_CONTEXT, isChecked.toString())
		}

		// spawn a Test notification
		createNotificationChannel()
		btnTestNotification.setOnClickListener {
			//val actionIntent = Intent(this, CustomActionListener::class.java)
			val actionIntent = Intent(this, CustomActionListener::class.java)

			val action = Notification.Action.Builder(null, "Custom action test",
					PendingIntent.getBroadcast(this, 0, actionIntent, FLAG_UPDATE_CURRENT ))
					.build()
			val notificationBuilder = Notification.Builder(this)
					.setSmallIcon(android.R.drawable.ic_menu_gallery)
					.setContentTitle("Test Notification")
					.setContentText("This is a test notification")
					.setSubText("SubText")
					.addAction(action)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				notificationBuilder.setChannelId(NOTIFICATION_CHANNEL_ID)
			}
			val notification = notificationBuilder.build()
			val manager = NotificationManagerCompat.from(this)
			manager.notify(1, notification)
		}

		btnHelp.setOnClickListener {
			val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://hufman.github.io/AndroidAutoIdrive/faq.html"))
			startActivity(intent)
		}

		// build list of discovered music apps
		appDiscoveryThread.start()
		listMusicApps.adapter = object: ArrayAdapter<MusicAppInfo>(this, R.layout.musicapp_listitem, displayedApps) {
			override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
				val appInfo = getItem(position)
				val layout = convertView ?: layoutInflater.inflate(R.layout.musicapp_listitem, parent,false)
				return if (appInfo != null) {
					layout.findViewById<ImageView>(R.id.imgMusicAppIcon).setImageDrawable(appInfo.icon)
					layout.findViewById<TextView>(R.id.txtMusicAppName).setText(appInfo.name)
					layout.findViewById<ImageView>(R.id.imgConnectable).visibility = if (appInfo.connectable) VISIBLE else GONE
					layout.findViewById<ImageView>(R.id.imgBrowseable).visibility = if (appInfo.browseable) VISIBLE else GONE
					layout.findViewById<ImageView>(R.id.imgSearchable).visibility = if (appInfo.searchable) VISIBLE else GONE
					layout
				} else {
					layout.findViewById<TextView>(R.id.txtMusicAppName).setText("Error")
					layout
				}
			}
		}

		listMusicAppsRefresh.setOnRefreshListener {
			appDiscoveryThread.forceDiscovery()
			handler.postDelayed({
				listMusicAppsRefresh.isRefreshing = false
			}, 2000)
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
		appDiscoveryThread.stopDiscovery()
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
					NOTIFICATION_CHANNEL_NAME,
					NotificationManager.IMPORTANCE_DEFAULT)

			val notificationManager = getSystemService(NotificationManager::class.java)
			notificationManager.createNotificationChannel(channel)

		}
	}
	fun onChangedSwitchNotifications(buttonView: CompoundButton, isChecked: Boolean) {
		AppSettings.saveSetting(this, AppSettings.KEYS.ENABLED_NOTIFICATIONS, isChecked.toString())
		if (isChecked) {
			// make sure we have permissions to read the notifications
			if (!hasNotificationPermission() || !UIState.notificationListenerConnected) {
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

		whenActivityStarted = System.currentTimeMillis()

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
		paneNotifications.visible = AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS].toBoolean()
		swNotificationPopup.isChecked = AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP].toBoolean()
		paneNotificationPopup.visible = AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP].toBoolean()
		swNotificationPopupPassenger.isChecked = AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER].toBoolean()
		swGMaps.isChecked = AppSettings[AppSettings.KEYS.ENABLED_GMAPS].toBoolean()
		paneGMaps.visible = AppSettings[AppSettings.KEYS.ENABLED_GMAPS].toBoolean()
		swGmapWidescreen.isChecked = AppSettings[AppSettings.KEYS.MAP_WIDESCREEN].toBoolean()

		val gmapStylePosition = resources.getStringArray(R.array.gmaps_styles).map { title ->
			title.toLowerCase().replace(' ', '_')
		}.indexOf(AppSettings[AppSettings.KEYS.GMAPS_STYLE].toLowerCase())
		swGmapSyle.setSelection(max(0, gmapStylePosition))

		swAudioContext.isChecked = AppSettings[AppSettings.KEYS.AUDIO_ENABLE_CONTEXT].toBoolean()

		val ageOfActivity = System.currentTimeMillis() - whenActivityStarted
		if (ageOfActivity > SECURITY_SERVICE_TIMEOUT && !SecurityService.success) {
			txtConnectionStatus.text = resources.getString(R.string.connectionStatusMissingConnectedApp)
			txtConnectionStatus.setBackgroundColor(resources.getColor(R.color.connectionError, null))
		} else if (!IDriveConnectionListener.isConnected) {
			txtConnectionStatus.text = resources.getString(R.string.connectionStatusWaiting)
			txtConnectionStatus.setBackgroundColor(resources.getColor(R.color.connectionWaiting, null))
		} else {
			txtConnectionStatus.text = when (IDriveConnectionListener.brand) {
				"bmw" -> resources.getString(R.string.notification_description_bmw)
				"mini" -> resources.getString(R.string.notification_description_mini)
				else -> resources.getString(R.string.notification_description)
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

	class CustomActionListener: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			Log.i(TAG, "Received custom action")
			Toast.makeText(context, "Custom Action press", Toast.LENGTH_SHORT).show()
		}
	}

	class AppDiscoveryThread(val context: Context, val callback: (List<MusicAppInfo>) -> Unit): HandlerThread("MusicAppDiscovery UI") {
		private lateinit var handler: Handler
		private lateinit var discovery: MusicAppDiscovery

		override fun onLooperPrepared() {
			handler = Handler(this.looper)
			discovery = MusicAppDiscovery(context, handler)
			discovery.listener = Runnable {
				scheduleRedraw()
			}
			discovery.discoverApps()
			discovery.probeApps(false)
		}

		private val redrawRunnable = Runnable {
			callback(discovery.apps)
		}

		private fun scheduleRedraw() {
			handler.removeCallbacks(redrawRunnable)
			handler.postDelayed(redrawRunnable, 100)
		}

		fun forceDiscovery() {
			discovery.cancelDiscovery()
			discovery.discoverApps()
			discovery.probeApps(true)
		}

		fun stopDiscovery() {
			discovery.cancelDiscovery()
			quitSafely()
		}
	}
}
