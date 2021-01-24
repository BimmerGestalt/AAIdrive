package me.hufman.androidautoidrive

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bmwgroup.connected.car.app.BrandType
import me.hufman.androidautoidrive.carapp.*
import me.hufman.androidautoidrive.carapp.assistant.AssistantControllerAndroid
import me.hufman.androidautoidrive.carapp.assistant.AssistantApp
import me.hufman.androidautoidrive.carapp.maps.MapAppMode
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.phoneui.*
import me.hufman.androidautoidrive.utils.GraphicsHelpersAndroid
import me.hufman.idriveconnectionkit.CDS
import me.hufman.idriveconnectionkit.android.CarAPIAppInfo
import me.hufman.idriveconnectionkit.android.CarAPIDiscovery
import me.hufman.idriveconnectionkit.android.IDriveConnectionReceiver
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
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

	var foregroundNotification: Notification? = null

	val appSettings = MutableAppSettingsReceiver(this)
	val securityAccess by lazy { SecurityAccess.getInstance(this) }
	val iDriveConnectionReceiver = IDriveConnectionReceiver()   // start listening to car connection, if the AndroidManifest listener didn't start
	var carProberThread: CarProber? = null

	val securityServiceThread by lazy { SecurityServiceThread(securityAccess) }

	val carInformationObserver = CarInformationObserver()
	val cdsObserver = CDSEventHandler { _, _ -> combinedCallback() }
	var threadCapabilities: CarThread? = null
	var carappCapabilities: CarInformationDiscovery? = null

	var notificationService: NotificationService? = null

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
		carInformationObserver.cdsData.removeEventHandler(CDS.VEHICLE.LANGUAGE, cdsObserver)
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
		// start some more services as the car language is discovered
		carInformationObserver.cdsData.addEventHandler(CDS.VEHICLE.LANGUAGE, 1000, cdsObserver)
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
					getString(R.string.notification_channel_connection),
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
		val notifyIntent = Intent(this, NavHostActivity::class.java).apply {
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

				// set the car app languages
				val locale = if (appSettings[AppSettings.KEYS.FORCE_CAR_LANGUAGE].isNotBlank()) {
					Locale.forLanguageTag(appSettings[AppSettings.KEYS.FORCE_CAR_LANGUAGE])
				} else if (appSettings[AppSettings.KEYS.PREFER_CAR_LANGUAGE].toBoolean() &&
							carInformationObserver.cdsData[CDS.VEHICLE.LANGUAGE] != null) {
					CDSVehicleLanguage.fromCdsProperty(carInformationObserver.cdsData[CDS.VEHICLE.LANGUAGE]).locale
				} else {
					null
				}
				L.loadResources(this, locale)

				// report car capabilities
				// also loads the car language
				startAny = startAny or startCarCapabilities()

				if (appSettings[AppSettings.KEYS.PREFER_CAR_LANGUAGE].toBoolean() &&
						carInformationObserver.cdsData[CDS.VEHICLE.LANGUAGE] == null) {
					// still waiting for language
				} else {
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
				}

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

	fun startCarCapabilities(): Boolean {
		synchronized(this) {
			if (threadCapabilities == null) {
				// receiver to save settings
				val carInformationUpdater = CarInformationUpdater(appSettings)

				// clear the capabilities to not start dependent services until it's ready
				threadCapabilities = CarThread("Capabilities") {
					Log.i(TAG, "Starting to discover car capabilities")
					val handler = threadCapabilities?.handler!!

					// receiver to receive capabilities and cds properties
					// wraps the CDSConnection with a Handler async wrapper
					val carInformationUpdater = object: CarInformationUpdater(appSettings) {
						override fun onCdsConnection(connection: CDSConnection) {
							super.onCdsConnection(CDSConnectionAsync(handler, connection))
						}
					}

					carappCapabilities = CarInformationDiscovery(iDriveConnectionReceiver, securityAccess,
							CarAppAssetManager(this, "smartthings"), carInformationUpdater)
					carappCapabilities?.onCreate()
				}
				threadCapabilities?.start()
			}
		}
		return true
	}

	fun stopCarCapabilities() {
		carappCapabilities?.onDestroy()
		carappCapabilities = null
		threadCapabilities?.quitSafely()
		threadCapabilities = null
	}

	fun startNotifications(): Boolean {
		if (carInformationObserver.capabilities.isNotEmpty() && notificationService == null) {
			notificationService = NotificationService(this, iDriveConnectionReceiver, securityAccess, carInformationObserver)
		}
		return notificationService?.start() ?: false
	}

	fun stopNotifications() {
		notificationService?.stop()
		notificationService = null
	}

	fun startMaps(): Boolean {
		if (carInformationObserver.capabilities.isNotEmpty() && mapService == null) {
			mapService = MapService(this, iDriveConnectionReceiver, securityAccess,
					MapAppMode(RHMIDimensions.create(carInformationObserver.capabilities), AppSettingsViewer()))
		}
		return mapService?.start() ?: false
	}

	fun stopMaps() {
		mapService?.stop()
		mapService = null
	}

	fun startMusic(): Boolean {
		if (carInformationObserver.capabilities.isNotEmpty() && musicService == null) {
			musicService = MusicService(applicationContext, iDriveConnectionReceiver, securityAccess,
					MusicAppMode.build(carInformationObserver.capabilities, applicationContext))
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
		}
	}

	private fun stopServiceNotification() {
		Log.i(TAG, "Hiding foreground notification")
		stopForeground(true)
		foregroundNotification = null
	}
}