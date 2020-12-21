package me.hufman.androidautoidrive

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bmwgroup.connected.car.app.BrandType
import me.hufman.androidautoidrive.carapp.assistant.AssistantControllerAndroid
import me.hufman.androidautoidrive.carapp.assistant.AssistantApp
import me.hufman.androidautoidrive.carapp.maps.MapAppMode
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.carapp.notifications.NotificationSettings
import me.hufman.androidautoidrive.notifications.CarNotificationControllerIntent
import me.hufman.androidautoidrive.notifications.NotificationListenerServiceImpl
import me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications
import me.hufman.androidautoidrive.carapp.notifications.ReadoutApp
import me.hufman.androidautoidrive.connections.BtStatus
import me.hufman.androidautoidrive.notifications.AudioPlayer
import me.hufman.androidautoidrive.phoneui.*
import me.hufman.androidautoidrive.utils.GraphicsHelpersAndroid
import me.hufman.idriveconnectionkit.android.CarAPIAppInfo
import me.hufman.idriveconnectionkit.android.CarAPIDiscovery
import me.hufman.idriveconnectionkit.android.IDriveConnectionReceiver
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import org.json.JSONObject
import java.lang.IllegalArgumentException
import java.util.*

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

	val appSettings = MutableAppSettingsReceiver(this)
	val securityAccess by lazy { SecurityAccess.getInstance(this) }
	val iDriveConnectionReceiver = IDriveConnectionReceiver()   // start listening to car connection, if the AndroidManifest listener didn't start
	var carProberThread: CarProber? = null

	val securityServiceThread by lazy { SecurityServiceThread(securityAccess) }

	val carInformationObserver = CarInformationObserver()
	var threadCapabilities: CarThread? = null
	var carappCapabilities: CarInformationDiscovery? = null

	var threadNotifications: CarThread? = null
	var carappNotifications: PhoneNotifications? = null
	var carappReadout: ReadoutApp? = null

	var mapService: MapService? = null

	var musicService: MusicService? = null

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
		return START_STICKY
	}

	override fun onDestroy() {
		handleActionStop()
		// one time things
		try {
			iDriveConnectionReceiver.unsubscribe(this)
		} catch (e: IllegalArgumentException) {
			// never started?
		}
		carProberThread?.quitSafely()
		super.onDestroy()
	}

	/**
	 * Start the service
	 */
	private fun handleActionStart() {
		Log.i(TAG, "Starting up service")
		createNotificationChannel()
		// subscribe to configuration changes
		appSettings.callback = {
			combinedCallback()
		}
		// set up connection listeners
		securityAccess.callback = {
			combinedCallback()
		}
		iDriveConnectionReceiver.callback = {
			combinedCallback()
		}
		// try connecting to the security service
		if (!securityServiceThread.isAlive) {
			securityServiceThread.start()
		}
		securityServiceThread.connect()
		// start up car connection listener
		announceCarAPI()
		iDriveConnectionReceiver.subscribe(this)
		startCarProber()
		// start some more services as the capabilities are known
		carInformationObserver.callback = {
			combinedCallback()
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

	private fun startCarProber() {
		if (carProberThread?.isAlive != true) {
			carProberThread = CarProber(securityAccess,
				CarAppAssetManager(this, "smartthings").getAppCertificateRaw("bmw")!!.readBytes(),
				CarAppAssetManager(this, "smartthings").getAppCertificateRaw("mini")!!.readBytes()
			).apply { start() }
		} else {
			carProberThread?.schedule(1000)
		}
	}

	private fun startServiceNotification(brand: String?, chassisCode: ChassisCode?) {
		Log.i(TAG, "Creating foreground notification")
		val notifyIntent = Intent(this, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
		}
		val foregroundNotificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
				.setOngoing(true)
				.setContentTitle(getText(R.string.notification_title))
				.setContentText(getText(R.string.notification_description))
				.setSmallIcon(R.drawable.ic_notify)
				.setPriority(NotificationCompat.PRIORITY_LOW)
				.setContentIntent(PendingIntent.getActivity(this, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT))

		if (brand?.toLowerCase(Locale.ROOT) == "bmw") foregroundNotificationBuilder.setContentText(getText(R.string.notification_description_bmw))
		if (brand?.toLowerCase(Locale.ROOT) == "mini") foregroundNotificationBuilder.setContentText(getText(R.string.notification_description_mini))

		if (chassisCode != null) {
			foregroundNotificationBuilder.setContentText(resources.getString(R.string.notification_description_chassiscode, chassisCode.toString()))
		}

		val foregroundNotification = foregroundNotificationBuilder.build()
		if (this.foregroundNotification?.extras?.getCharSequence(Notification.EXTRA_TEXT) !=
				foregroundNotification?.extras?.getCharSequence(Notification.EXTRA_TEXT)) {
			startForeground(ONGOING_NOTIFICATION_ID, foregroundNotification)
		}
		this.foregroundNotification = foregroundNotification
	}

	fun combinedCallback() {
		synchronized(MainService::class.java) {
			if (iDriveConnectionReceiver.isConnected && securityAccess.isConnected()) {
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

				// start navigation handler
				startNavigationListener()

				// check if we are idle and should shut down
				if (startAny ){
					startServiceNotification(iDriveConnectionReceiver.brand, ChassisCode.fromCode(carInformationObserver.capabilities["vehicle.type"] ?: "Unknown"))
				} else {
					Log.i(TAG, "No apps are enabled, skipping the service start")
					stopServiceNotification()
					stopSelf()
				}

				// show a donation popup, if it's time
				DonationRequest(this).countUsage()
			} else {
				Log.d(TAG, "Not fully connected: IDrive:${iDriveConnectionReceiver.isConnected} SecurityService:${securityAccess.isConnected()}")

				stopCarApps()
			}
		}
	}

	fun startCarCapabilities() {
		synchronized(this) {
			if (threadCapabilities == null) {
				// clear the capabilities to not start dependent services until it's ready
				threadCapabilities = CarThread("Capabilities") {
					Log.i(TAG, "Starting to discover car capabilities")

					carappCapabilities = CarInformationDiscovery(iDriveConnectionReceiver, securityAccess,
							CarAppAssetManager(this, "smartthings"),
							object: CarInformationDiscoveryListener {
						override fun onCapabilities(capabilities: Map<String, String?>) {
							// update the known capabilities
							// which triggers a callback to start more service modules
							carInformationObserver.capabilities = capabilities.mapValues { it.value ?: "" }

							// update the notification
							startServiceNotification(iDriveConnectionReceiver.brand, ChassisCode.fromCode(carInformationObserver.capabilities["vehicle.type"] ?: "Unknown"))
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
		threadCapabilities?.quitSafely()
		threadCapabilities = null
	}

	fun startNotifications(): Boolean {
		val enabled = appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS].toBoolean() &&
				Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
		if (enabled) {
			synchronized(this) {
				if (carInformationObserver.capabilities.isNotEmpty() && threadNotifications?.isAlive != true) {
					threadNotifications = CarThread("Notifications") {
						Log.i(TAG, "Starting notifications app")
						val handler = threadNotifications?.handler
						if (handler == null) {
							Log.e(TAG, "CarThread Handler is null?")
						}
						val notificationSettings = NotificationSettings(carInformationObserver.capabilities, BtStatus(this) {}, MutableAppSettingsReceiver(this, handler))
						notificationSettings.btStatus.register()
						carappNotifications = PhoneNotifications(iDriveConnectionReceiver, securityAccess,
								CarAppAssetManager(this, "basecoreOnlineServices"),
								PhoneAppResourcesAndroid(this),
								GraphicsHelpersAndroid(),
								CarNotificationControllerIntent(this),
								AudioPlayer(this),
								notificationSettings)
						if (handler != null) {
							carappNotifications?.onCreate(this, handler)
						}
						// request an initial draw
						sendBroadcast(Intent(NotificationListenerServiceImpl.INTENT_REQUEST_DATA))

						handler?.post {
							// start up the readout app
							// using a handler to automatically handle shutting down during init
							val carappReadout = ReadoutApp(iDriveConnectionReceiver, securityAccess,
									CarAppAssetManager(this, "news"))
							carappNotifications?.readoutInteractions?.readoutController = carappReadout.readoutController
							this.carappReadout = carappReadout
						}
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
		carappNotifications?.notificationSettings?.btStatus?.unregister()
		carappNotifications?.onDestroy(this)
		carappNotifications = null
		carappReadout?.onDestroy()
		carappReadout = null
		// if we caught it during initialization, kill it again
		threadNotifications?.post {
			stopNotifications()
		}
		threadNotifications?.quitSafely()
		threadNotifications = null
	}

	fun startMaps(): Boolean {
		if (carInformationObserver.capabilities.isNotEmpty() && mapService == null) {
			mapService = MapService(this, iDriveConnectionReceiver, securityAccess,
					MapAppMode(carInformationObserver.capabilities, AppSettingsViewer()))
		}
		return mapService?.start() ?: false
	}

	fun stopMaps() {
		mapService?.stop()
		mapService = null
	}

	fun startMusic(): Boolean {
		if (carInformationObserver.capabilities.isNotEmpty() && musicService == null) {
			musicService = MusicService(this, iDriveConnectionReceiver, securityAccess,
					MusicAppMode.build(carInformationObserver.capabilities, this))
			musicService?.start()
		}
		return true
	}
	fun stopMusic() {
		musicService?.stop()
		musicService = null
	}

	fun startAssistant(): Boolean {
		synchronized(this) {
			if (threadAssistant == null) {
				threadAssistant = CarThread("Assistant") {
					Log.i(TAG, "Starting to discover car capabilities")

					carappAssistant = AssistantApp(iDriveConnectionReceiver, securityAccess,
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
		threadAssistant?.quitSafely()
		threadAssistant = null
	}

	fun startNavigationListener() {
		if (carInformationObserver.capabilities["navi"] == "true") {
			if (iDriveConnectionReceiver.brand?.toLowerCase(Locale.ROOT) == "bmw") {
				packageManager.setComponentEnabledSetting(
						ComponentName(BuildConfig.APPLICATION_ID, "${BuildConfig.APPLICATION_ID}.phoneui.NavActivityBMW"),
						PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
				)
			} else if (iDriveConnectionReceiver.brand?.toLowerCase(Locale.ROOT) == "mini") {
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
			appSettings.callback = null
			securityServiceThread.disconnect()
		}
	}

	private fun stopServiceNotification() {
		Log.i(TAG, "Hiding foreground notification")
		stopForeground(true)
		foregroundNotification = null
	}
}