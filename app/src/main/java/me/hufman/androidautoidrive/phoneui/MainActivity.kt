package me.hufman.androidautoidrive.phoneui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.listMusicApps
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.assistant.AssistantAppInfo
import me.hufman.androidautoidrive.carapp.assistant.AssistantControllerAndroid
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import java.lang.IllegalStateException
import kotlin.math.max

class MainActivity : AppCompatActivity() {

	companion object {
		const val INTENT_REDRAW = "me.hufman.androidautoidrive.REDRAW"
		const val TAG = "MainActivity"
		const val NOTIFICATION_SERVICE_TIMEOUT = 1000
		const val SECURITY_SERVICE_TIMEOUT = 1000
		const val REDRAW_INTERVAL = 5000L
		const val REQUEST_LOCATION = 4000

		const val NOTIFICATION_CHANNEL_ID = "TestNotification"
		const val NOTIFICATION_CHANNEL_NAME = "Test Notification"
	}
	val handler = Handler()
	val redrawListener = RedrawListener()
	val redrawTask = RedrawTask()
	val displayedMusicApps = ArrayList<MusicAppInfo>()
	val appDiscoveryThread = AppDiscoveryThread(this) { appDiscovery ->
		handler.post {
			displayedMusicApps.clear()
			displayedMusicApps.addAll(appDiscovery.validApps)
			listMusicApps.invalidateViews() // redraw the app list
		}
	}

	val assistantController by lazy { AssistantControllerAndroid(this, PhoneAppResourcesAndroid(this)) }
	val displayedAssistantApps = ArrayList<AssistantAppInfo>()
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
		btnConfigureNotifications.setOnClickListener {
			val intent = Intent(this, ConfigureNotificationsActivity::class.java)
			startActivity(intent)
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

		btnConfigureMusic.setOnClickListener {
			val intent = Intent(this, MusicActivity::class.java)
			startActivity(intent)
		}
		btnHelp.setOnClickListener {
			val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://hufman.github.io/AndroidAutoIdrive/faq.html"))
			startActivity(intent)
		}

		// build list of discovered music apps
		appDiscoveryThread.start()

		listMusicApps.setOnItemClickListener { adapterView, view, i, l ->
			val appInfo = adapterView.adapter.getItem(i) as? MusicAppInfo
			if (appInfo != null) {
				UIState.selectedMusicApp = appInfo
				val intent = Intent(this, MusicPlayerActivity::class.java)
				startActivity(intent)
			}
		}
		listMusicApps.adapter = object: ArrayAdapter<MusicAppInfo>(this, R.layout.musicapp_listitem, displayedMusicApps) {
			val animationLoopCallback = object: Animatable2.AnimationCallback() {
				override fun onAnimationEnd(drawable: Drawable?) {
					handler.post { (drawable as AnimatedVectorDrawable).start() }
				}
			}
			val equalizerStatic = resources.getDrawable(R.drawable.ic_equalizer_black_24dp, null)
			val equalizerAnimated = (resources.getDrawable(R.drawable.ic_dancing_equalizer, null) as AnimatedVectorDrawable).apply {
				this.registerAnimationCallback(animationLoopCallback)
			}

			override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
				val appInfo = getItem(position)
				val layout = convertView ?: layoutInflater.inflate(R.layout.musicapp_griditem, parent,false)
				return if (appInfo != null) {
					layout.findViewById<ImageView>(R.id.imgMusicAppIcon).setImageDrawable(appInfo.icon)
					layout.findViewById<ImageView>(R.id.imgMusicAppIcon).contentDescription = appInfo.name

					if (appInfo.packageName == appDiscoveryThread.discovery?.musicSessions?.getPlayingApp()?.packageName) {
						layout.findViewById<ImageView>(R.id.imgNowPlaying).setImageDrawable(equalizerAnimated)
						equalizerAnimated.start()
						layout.findViewById<ImageView>(R.id.imgNowPlaying).visibility = View.VISIBLE
					} else {
						layout.findViewById<ImageView>(R.id.imgNowPlaying).setImageDrawable(equalizerStatic)
						layout.findViewById<ImageView>(R.id.imgNowPlaying).visibility = View.GONE
					}
					layout
				} else {
					layout.findViewById<TextView>(R.id.txtMusicAppName).setText("Error")
					layout
				}
			}
		}

		listAssistantApps.adapter = object: ArrayAdapter<AssistantAppInfo>(this, R.layout.assistantapp_listitem, displayedAssistantApps) {
			override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
				val appInfo = getItem(position)
				val layout = convertView ?: layoutInflater.inflate(R.layout.assistantapp_listitem, parent,false)
				return if (appInfo != null) {
					layout.findViewById<ImageView>(R.id.imgAssistantAppIcon).setImageDrawable(appInfo.icon)
					layout.findViewById<ImageView>(R.id.imgAssistantAppIcon).contentDescription = appInfo.name
					layout.findViewById<TextView>(R.id.txtAssistantAppName).text = appInfo.name
					layout.findViewById<ImageView>(R.id.imgAssistantSettingsIcon).visible = assistantController.supportsSettings(appInfo)

					layout.findViewById<ImageView>(R.id.imgAssistantSettingsIcon).setOnClickListener {
						assistantController.openSettings(appInfo)
					}
					layout
				} else {
					layout.findViewById<TextView>(R.id.txtAssistantAppName).setText("Error")
					layout
				}
			}
		}
		listAssistantApps.setOnItemClickListener { adapterView, view, i, l ->
			val appInfo = adapterView.adapter.getItem(i) as? AssistantAppInfo
			if (appInfo != null) {
				assistantController.triggerAssistant(appInfo)
			}
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

	fun onChangedSwitchNotifications(buttonView: CompoundButton, isChecked: Boolean) {
		AppSettings.saveSetting(this, AppSettings.KEYS.ENABLED_NOTIFICATIONS, isChecked.toString())
		if (isChecked) {
			// make sure we have permissions to read the notifications
			val ageOfActivity = System.currentTimeMillis() - whenActivityStarted
			if (ageOfActivity > SECURITY_SERVICE_TIMEOUT && (!hasNotificationPermission() || !UIState.notificationListenerConnected)) {
				promptNotificationPermission()
			} else {
				startMainService()
			}
		} else {
			startMainService()
		}
	}

	fun promptNotificationPermission() {
		startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
	}

	fun hasNotificationPermission(): Boolean {
		return UIState.notificationListenerConnected && NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
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

		redraw()

		// update the music apps list, including any music sessions
		appDiscoveryThread.discovery()

		// reload assistants
		displayedAssistantApps.clear()
		displayedAssistantApps.addAll(assistantController.getAssistants().toList().sortedBy { it.name })
		listAssistantApps.emptyView = txtEmptyAssistantApps
		listAssistantApps.invalidateViews()

		// try starting the service, to try connecting to the car with current app settings
		// for example, after we resume from enabling the notification
		startMainService()
	}

	fun redraw() {
		val ageOfActivity = System.currentTimeMillis() - whenActivityStarted

		// reset the Notification setting to false if we don't have permission
		// wait a bit to make sure the Notification Listener actually is running
		// sometimes when restarting, the listener takes a bit to start up
		if (ageOfActivity > NOTIFICATION_SERVICE_TIMEOUT && !hasNotificationPermission()) {
			AppSettings.saveSetting(this, AppSettings.KEYS.ENABLED_NOTIFICATIONS, "false")
		}
		// reset the GMaps setting if we don't have permission
		if (!hasLocationPermission()) {
			AppSettings.saveSetting(this, AppSettings.KEYS.ENABLED_GMAPS, "false")
		}

		swMessageNotifications.isChecked = AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS].toBoolean()
		paneNotifications.visible = AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS].toBoolean()
		swGMaps.isChecked = AppSettings[AppSettings.KEYS.ENABLED_GMAPS].toBoolean()
		paneGMaps.visible = AppSettings[AppSettings.KEYS.ENABLED_GMAPS].toBoolean()
		swGmapWidescreen.isChecked = AppSettings[AppSettings.KEYS.MAP_WIDESCREEN].toBoolean()

		val gmapStylePosition = resources.getStringArray(R.array.gmaps_styles).map { title ->
			title.toLowerCase().replace(' ', '_')
		}.indexOf(AppSettings[AppSettings.KEYS.GMAPS_STYLE].toLowerCase())
		swGmapSyle.setSelection(max(0, gmapStylePosition))

		if (ageOfActivity > SECURITY_SERVICE_TIMEOUT && !SecurityAccess.getInstance(this).isConnected()) {
			txtConnectionStatus.text = resources.getString(R.string.connectionStatusMissingConnectedApp)
			txtConnectionStatus.setBackgroundColor(resources.getColor(R.color.connectionError, null))
		} else if (!IDriveConnectionListener.isConnected) {
			txtConnectionStatus.text = resources.getString(R.string.connectionStatusWaiting)
			txtConnectionStatus.setBackgroundColor(resources.getColor(R.color.connectionWaiting, null))
		} else {
			val chassisCode = ChassisCode.fromCode(DebugStatus.carCapabilities["vehicle.type"] ?: "Unknown")
			txtConnectionStatus.text = if (chassisCode != null) {
				resources.getString(R.string.notification_description_chassiscode, chassisCode.toString())
			} else {
				when (IDriveConnectionListener.brand?.toLowerCase()) {
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
