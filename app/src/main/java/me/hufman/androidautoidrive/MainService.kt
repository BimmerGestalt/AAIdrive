package me.hufman.androidautoidrive

import ChassisCode
import android.app.*
import android.app.Notification.PRIORITY_LOW
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.bmwgroup.connected.car.app.BrandType
import me.hufman.androidautoidrive.carapp.assistant.AssistantControllerAndroid
import me.hufman.androidautoidrive.carapp.assistant.AssistantApp
import me.hufman.androidautoidrive.carapp.notifications.CompletelySuggestionStrategy
import me.hufman.androidautoidrive.notifications.CarNotificationControllerIntent
import me.hufman.androidautoidrive.notifications.NotificationListenerServiceImpl
import me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications
import me.hufman.androidautoidrive.carapp.notifications.WordListPersistence
import me.hufman.androidautoidrive.phoneui.*
import me.hufman.completelywordlist.CompletelyWordList
import me.hufman.idriveconnectionkit.android.CarAPIAppInfo
import me.hufman.idriveconnectionkit.android.CarAPIDiscovery
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import org.json.JSONObject
import java.lang.IllegalArgumentException

class MainService: Service() {
	companion object {
		const val TAG = "MainService"

		const val ACTION_START = "me.hufman.androidautoidrive.MainService.start"
		const val ACTION_STOP = "me.hufman.androidautoidrive.MainService.stop"
	}
	val ONGOING_NOTIFICATION_ID = 20503
	val NOTIFICATION_CHANNEL_ID = "ConnectionNotification"
	val NOTIFICATION_CHANNEL_NAME = "Car Connection Status"

	var foregroundNotification: Notification? = null

	val securityAccess = SecurityAccess.getInstance(this)
	val idriveConnectionListener = IDriveConnectionListener()   // start listening to car connection, if the AndroidManifest listener didn't start
	val carProberThread by lazy {
		CarProber(securityAccess,
			CarAppAssetManager(this, "smartthings").getAppCertificateRaw("bmw")!!.readBytes(),
			CarAppAssetManager(this, "smartthings").getAppCertificateRaw("mini")!!.readBytes()
		)
	}

	val securityServiceThread = SecurityServiceThread(securityAccess)

	var threadCapabilities: CarThread? = null
	var carappCapabilities: CarInformationDiscovery? = null
	var carCapabilities: Map<String, String?> = mapOf()

	var threadNotifications: CarThread? = null
	var carappNotifications: PhoneNotifications? = null

	var mapService = MapService(this, securityAccess)

	var musicService = MusicService(this, securityAccess)

	var threadAssistant: CarThread? = null
	var carappAssistant: AssistantApp? = null


	override fun onBind(intent: Intent?): IBinder? {
		return null
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		Analytics.init(this)

		// load the emoji dictionary
		UnicodeCleaner.init(this)

		val action = intent?.action ?: ""
		if (action == ACTION_START) {
			handleActionStart()
		} else if (action == ACTION_STOP) {
			handleActionStop()
		}
		return Service.START_STICKY
	}

	override fun onDestroy() {
		handleActionStop()
		// one time things
		try {
			idriveConnectionListener.unsubscribe(this)
		} catch (e: IllegalArgumentException) {
			// never started?
		}
		carProberThread.quitSafely()
		super.onDestroy()
	}

	/**
	 * Start the service
	 */
	private fun handleActionStart() {
		Log.i(TAG, "Starting up service")
		createNotificationChannel()
		// set up connection listeners
		securityAccess.listener = Runnable {
			combinedCallback()
		}
		IDriveConnectionListener.callback = Runnable {
			combinedCallback()
		}
		// try connecting to the security service
		if (!securityServiceThread.isAlive) {
			securityServiceThread.start()
		}
		securityServiceThread.connect()
		// start up car connection listener
		announceCarAPI()
		idriveConnectionListener.subscribe(this)
		if (!carProberThread.isAlive) {
			carProberThread.start()
		} else {
			carProberThread.schedule(1000)
		}
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
					NOTIFICATION_CHANNEL_NAME,
					NotificationManager.IMPORTANCE_MIN)

			val notificationManager = getSystemService(NotificationManager::class.java)
			notificationManager.createNotificationChannel(channel)
		}
	}

	private fun announceCarAPI() {
		val myApp = CarAPIAppInfo(
				id = packageName,
				title = "Android Auto IDrive",
				category = "OnlineServices",
				brandType = BrandType.ALL,
				version = "v2",
				rhmiVersion = "v2",
				connectIntentName = "me.hufman.androidautoidrive.CarConnectionListener_START",
				disconnectIntentName = "me.hufman.androidautoidrive.CarConnectionListener_STOP",
				appIcon = null
		)
		CarAPIDiscovery.announceApp(this, myApp)
	}

	private fun startServiceNotification(brand: String?, chassisCode: ChassisCode?) {
		Log.i(TAG, "Creating foreground notification")
		val notifyIntent = Intent(this, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
		}
		val foregroundNotificationBuilder = Notification.Builder(this)
				.setOngoing(true)
				.setContentTitle(getText(R.string.notification_title))
				.setContentText(getText(R.string.notification_description))
				.setSmallIcon(R.drawable.ic_notify)
				.setPriority(PRIORITY_LOW)
				.setContentIntent(PendingIntent.getActivity(this, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT))
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			foregroundNotificationBuilder.setChannelId(NOTIFICATION_CHANNEL_ID)
		}

		if (brand?.toLowerCase() == "bmw") foregroundNotificationBuilder.setContentText(getText(R.string.notification_description_bmw))
		if (brand?.toLowerCase() == "mini") foregroundNotificationBuilder.setContentText(getText(R.string.notification_description_mini))

		if (chassisCode != null) {
			foregroundNotificationBuilder.setContentText(resources.getString(R.string.notification_description_chassiscode, chassisCode.toString()))
		}

		foregroundNotification = foregroundNotificationBuilder.build()
		startForeground(ONGOING_NOTIFICATION_ID, foregroundNotification)
	}

	fun combinedCallback() {
		synchronized(MainService::class.java) {
			if (IDriveConnectionListener.isConnected && securityAccess.isConnected()) {
				var startAny = false

				AppSettings.loadSettings(this)
				L.loadResources(this)

				// report car capabilities
				startCarCapabilities()

				// start notifications
				startAny = startAny or startNotifications()

				// start maps
				startAny = startAny or startMaps()

				// start music
				startAny = startAny or startMusic()

				// start assistant
				startAny = startAny or startAssistant()

				// check if we are idle and should shut down
				if (startAny ){
					startServiceNotification(IDriveConnectionListener.brand, ChassisCode.fromCode(carCapabilities["vehicle.type"] ?: "Unknown"))
				} else {
					Log.i(TAG, "No apps are enabled, skipping the service start")
					stopServiceNotification()
					stopSelf()
				}

				// show a donation popup, if it's time
				DonationRequest(this).countUsage()
			} else {
				Log.d(TAG, "Not fully connected: IDrive:${IDriveConnectionListener.isConnected} SecurityService:${securityAccess.isConnected()}")

				stopCarApps()
			}
		}
		sendBroadcast(Intent(MainActivity.INTENT_REDRAW))   // tell the UI we are connected
	}

	fun startCarCapabilities() {
		synchronized(this) {
			if (threadCapabilities == null) {
				threadCapabilities = CarThread("Capabilities") {
					Log.i(TAG, "Starting to discover car capabilities")

					carappCapabilities = CarInformationDiscovery(securityAccess,
							CarAppAssetManager(this, "smartthings"),
							object: CarInformationDiscoveryListener {
						override fun onCapabilities(capabilities: Map<String, String?>) {
							synchronized(DebugStatus.carCapabilities) {
								DebugStatus.carCapabilities.clear()
								DebugStatus.carCapabilities.putAll(capabilities.mapValues { it.value ?: "null" })
							}
							SetupActivity.redraw(this@MainService)
							// update the notification
							carCapabilities = capabilities
							startServiceNotification(IDriveConnectionListener.brand, ChassisCode.fromCode(carCapabilities["vehicle.type"] ?: "Unknown"))

							// enable navigation listener, if supported
							startNavigationListener()
						}

						override fun onCdsProperty(propertyName: String, propertyValue: String, parsedValue: JSONObject?) {
							if (propertyName == "navigation.guidanceStatus" && parsedValue?.getInt("guidanceStatus") == 1) {
								sendBroadcast(Intent(NavIntentActivity.INTENT_NAV_SUCCESS))
							}
						}

					})
					carappCapabilities?.onCreate()
				}
				threadCapabilities?.start()
			}
		}
	}

	fun stopCarCapabilities() {
		carappCapabilities?.onDestroy()
		carappCapabilities = null
		threadCapabilities?.handler?.looper?.quitSafely()
		threadCapabilities = null
	}

	fun startNotifications(): Boolean {
		if (AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS].toBoolean() &&
				Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true) {
			synchronized(this) {
				if (threadNotifications == null) {
					threadNotifications = CarThread("Notifications") {
						Log.i(TAG, "Starting notifications app")
						val handler = threadNotifications?.handler
						if (handler == null) {
							Log.e(TAG, "CarThread Handler is null?")
						}
						val autocomplete = CompletelyWordList.createEngine()
						val suggestionStrategy = CompletelySuggestionStrategy(autocomplete)
						carappNotifications = PhoneNotifications(securityAccess,
								CarAppAssetManager(this, "basecoreOnlineServices"),
								PhoneAppResourcesAndroid(this),
								GraphicsHelpersAndroid(),
								CarNotificationControllerIntent(this),
								suggestionStrategy,
								MutableAppSettings(this, handler))
						if (handler != null) {
							carappNotifications?.onCreate(this, handler)
						}
						// request an initial draw
						sendBroadcast(Intent(NotificationListenerServiceImpl.INTENT_REQUEST_DATA))

						// load up the dictionary, specifically after an initial draw
						handler?.postDelayed({
							val persistence = WordListPersistence(this@MainService)
							val language = AppSettings[AppSettings.KEYS.INPUT_COMPLETION_LANGUAGE]
							if (persistence.isLanguageDownloaded(language)) {
								persistence.load(autocomplete, language)
							}
						}, 1000)
					}
					threadNotifications?.start()
				}
			}
			return true
		} else {    // we should not run the service
			if (threadNotifications != null) {
				Log.i(TAG, "Notifications app needs to be shut down...")
				stopNotifications()
			}
			return false
		}
	}

	fun stopNotifications() {
		carappNotifications?.onDestroy(this)
		carappNotifications = null
		threadNotifications?.handler?.looper?.quitSafely()
		threadNotifications = null
	}

	fun startMaps(): Boolean {
		return mapService.start()
	}

	fun stopMaps() {
		mapService.stop()
	}

	fun startMusic(): Boolean {
		return musicService.start()
	}
	fun stopMusic() {
		musicService.stop()
	}

	fun startAssistant(): Boolean {
		synchronized(this) {
			if (threadAssistant == null) {
				threadAssistant = CarThread("Assistant") {
					Log.i(TAG, "Starting to discover car capabilities")

					carappAssistant = AssistantApp(securityAccess,
							CarAppAssetManager(this, "basecoreOnlineServices"),
							AssistantControllerAndroid(this, PhoneAppResourcesAndroid(this)),
							GraphicsHelpersAndroid())
					carappAssistant?.onCreate()
				}
				threadAssistant?.start()
			}
		}
		return true
	}

	fun stopAssistant() {
		carappAssistant?.onDestroy()
		carappAssistant = null
		threadAssistant?.handler?.looper?.quitSafely()
		threadAssistant = null
	}

	fun startNavigationListener() {
		if (carCapabilities["navi"] == "true") {
			if (IDriveConnectionListener.brand?.toLowerCase() == "bmw") {
				packageManager.setComponentEnabledSetting(
						ComponentName(BuildConfig.APPLICATION_ID, "${BuildConfig.APPLICATION_ID}.phoneui.NavActivityBMW"),
						PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
				)
			} else if (IDriveConnectionListener.brand?.toLowerCase() == "mini") {
				packageManager.setComponentEnabledSetting(
						ComponentName(BuildConfig.APPLICATION_ID, "${BuildConfig.APPLICATION_ID}.phoneui.NavActivityMINI"),
						PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
				)
			}
		}
	}

	fun stopNavigationListener() {
		packageManager.setComponentEnabledSetting(
				ComponentName(BuildConfig.APPLICATION_ID, "${BuildConfig.APPLICATION_ID}.phoneui.NavActivityBMW"),
				PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP
		)
		packageManager.setComponentEnabledSetting(
				ComponentName(BuildConfig.APPLICATION_ID, "${BuildConfig.APPLICATION_ID}.phoneui.NavActivityMINI"),
				PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP
		)
	}

	private fun stopCarApps() {
		stopCarCapabilities()
		stopNotifications()
		stopMaps()
		stopMusic()
		stopAssistant()
		stopNavigationListener()
		stopServiceNotification()
	}

	/**
	 * Stop the service
	 */
	private fun handleActionStop() {
		Log.i(TAG, "Shutting down service")
		synchronized(MainService::class.java) {
			stopCarApps()
			securityAccess.listener = Runnable {}
			securityServiceThread.disconnect()
		}
	}

	private fun stopServiceNotification() {
		Log.i(TAG, "Hiding foreground notification")
		stopForeground(true)
	}
}