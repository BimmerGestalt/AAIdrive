package me.hufman.androidautoidrive

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bmwgroup.connected.car.app.BrandType
import io.bimmergestalt.idriveconnectkit.CDS
import io.bimmergestalt.idriveconnectkit.android.CarAPIAppInfo
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionReceiver
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import me.hufman.androidautoidrive.addons.AddonsService
import me.hufman.androidautoidrive.carapp.*
import me.hufman.androidautoidrive.carapp.assistant.AssistantAppService
import me.hufman.androidautoidrive.carapp.carinfo.CarInformationDiscoveryService
import me.hufman.androidautoidrive.carapp.maps.MapAppService
import me.hufman.androidautoidrive.carapp.music.MusicAppService
import me.hufman.androidautoidrive.carapp.notifications.NotificationAppService
import me.hufman.androidautoidrive.connections.BtStatus
import me.hufman.androidautoidrive.phoneui.DonationRequest
import me.hufman.androidautoidrive.phoneui.NavHostActivity
import java.util.*

class MainService: Service() {
	companion object {
		const val TAG = "MainService"

		const val ACTION_START = "me.hufman.androidautoidrive.MainService.start"
		const val ACTION_STOP = "me.hufman.androidautoidrive.MainService.stop"
		const val EXTRA_FOREGROUND = "EXTRA_FOREGROUND"

		const val CONNECTED_PROBE_TIMEOUT: Long = 2 * 60 * 1000     // if Bluetooth is connected
		const val DISCONNECTED_PROBE_TIMEOUT: Long = 20 * 1000      // if Bluetooth is not connected
		const val STARTUP_DEBOUNCE = 1500
	}

	val ONGOING_NOTIFICATION_ID = 20503
	val NOTIFICATION_CHANNEL_ID = "ConnectionNotification"

	var foregroundNotification: Notification? = null

	val appSettings by lazy { MutableAppSettingsReceiver(applicationContext) }
	val securityAccess by lazy { SecurityAccess.getInstance(applicationContext) }
	val iDriveConnectionReceiver = IDriveConnectionReceiver()   // start listening to car connection, if the AndroidManifest listener didn't start
	var carProberThread: CarProber? = null

	val securityServiceThread by lazy { SecurityServiceThread(securityAccess) }

	val carInformationObserver = CarInformationObserver()
	val carInformationUpdater by lazy { CarInformationUpdater(appSettings) }
	val cdsObserver = CDSEventHandler { _, _ -> combinedCallback() }

	var moduleServiceBindings = HashMap<Class<out Service>, ServiceConnection>()
	var addonsService: AddonsService? = null

	// reschedule a combinedCallback to make sure enough time has passed
	var connectionTime: Long? = null
	val combinedCallbackRunnable = Runnable {
		combinedCallback()
	}

	// detect if the phone has suspended or destroyed the app
	val backgroundInterruptionDetection by lazy { BackgroundInterruptionDetection.build(applicationContext) }

	// shut down probing after a timeout
	val handler = Handler(Looper.getMainLooper())
	val shutdownTimeout = Runnable {
		if (!iDriveConnectionReceiver.isConnected || !securityAccess.isConnected()) {
			stopSelf()
		}
	}

	// triggers repeated BLUETOOTH_UUID responses to the Connected app
	// which should trigger it to connect to the car
	val btStatus by lazy { BtStatus(applicationContext, {}).apply { register() } }
	val btfetchUuidsWithSdp: Runnable by lazy { Runnable {
		handler.removeCallbacks(btfetchUuidsWithSdp)
		if (!iDriveConnectionReceiver.isConnected) {
			btStatus.fetchUuidsWithSdp()

			// schedule as long as the car is connected
			if (btStatus.isA2dpConnected) {
				handler.postDelayed(btfetchUuidsWithSdp, 5000)
			}
		}
	} }

	override fun onCreate() {
		super.onCreate()

		backgroundInterruptionDetection.detectKilledPreviously()

		// only register listeners a single time

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
		// start some more services as the capabilities are known
		carInformationObserver.callback = {
			combinedCallback()
		}
		// start some more services as the car language is discovered
		carInformationObserver.cdsData.addEventHandler(CDS.VEHICLE.LANGUAGE, 1000, cdsObserver)
	}

	override fun onBind(intent: Intent?): IBinder? {
		return null
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		AppSettings.loadSettings(applicationContext)
		if (AppSettings[AppSettings.KEYS.ENABLED_ANALYTICS].toBoolean()) {
			Analytics.init(applicationContext)
		}

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
		// undo one time things
		carInformationObserver.cdsData.removeEventHandler(CDS.VEHICLE.LANGUAGE, cdsObserver)
		carInformationObserver.callback = { }
		iDriveConnectionReceiver.callback = { }
		try {
			iDriveConnectionReceiver.unsubscribe(applicationContext)
		} catch (e: IllegalArgumentException) {
			// never started?
		}
		securityAccess.callback = {}
		appSettings.callback = null

		carProberThread?.quitSafely()
		btStatus.unregister()

		backgroundInterruptionDetection.stop()

		// close the notification
		stopServiceNotification()
		super.onDestroy()
	}

	/**
	 * Start the service
	 */
	private fun handleActionStart() {
		Log.i(TAG, "Starting up service $this")
		// show the notification, so we can be startForegroundService'd
		createNotificationChannel()
		// try connecting to the security service
		if (!securityServiceThread.isAlive) {
			securityServiceThread.start()
		}
		securityServiceThread.connect()
		// start up car connection listener
		announceCarAPI()
		iDriveConnectionReceiver.subscribe(applicationContext)
		startCarProber()
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
		myApp.announceApp(applicationContext)
	}

	private fun startCarProber() {
		if (carProberThread?.isAlive != true) {
			carProberThread = CarProber(securityAccess,
				CarAppAssetResources(applicationContext, "smartthings").getAppCertificateRaw("bmw")!!.readBytes(),
				CarAppAssetResources(applicationContext, "smartthings").getAppCertificateRaw("mini")!!.readBytes()
			).apply { start() }
		} else {
			carProberThread?.schedule(1000)
		}
	}

	private fun startServiceNotification(brand: String?, chassisCode: ChassisCode?) {
		Log.i(TAG, "Creating foreground notification")
		val notifyIntent = Intent(applicationContext, NavHostActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
		}
		val foregroundNotificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
				.setOngoing(true)
				.setContentTitle(getText(R.string.notification_title))
				.setContentText(getText(R.string.notification_description))
				.setSmallIcon(R.drawable.ic_notify)
				.setPriority(NotificationCompat.PRIORITY_LOW)
				.setContentIntent(PendingIntent.getActivity(applicationContext, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT))

		if (!iDriveConnectionReceiver.isConnected) {
			// show a notification even if we aren't connected, in case we were called with startForegroundService
			// the combinedCallback will hide it right away, but it's probably enough
			foregroundNotificationBuilder.setContentText(getString(R.string.connectionStatusWaiting))
			foregroundNotificationBuilder.setOngoing(false) // able to swipe away if we aren't currently connected
		} else {
			if (brand?.lowercase(Locale.ROOT) == "bmw") foregroundNotificationBuilder.setContentText(getText(R.string.notification_description_bmw))
			if (brand?.lowercase(Locale.ROOT) == "mini") foregroundNotificationBuilder.setContentText(getText(R.string.notification_description_mini))

			if (chassisCode != null) {
				foregroundNotificationBuilder.setContentText(resources.getString(R.string.notification_description_chassiscode, chassisCode.toString()))
			}
		}

		val foregroundNotification = foregroundNotificationBuilder.build()
		if (this.foregroundNotification?.extras?.getCharSequence(Notification.EXTRA_TEXT) !=
				foregroundNotification.extras?.getCharSequence(Notification.EXTRA_TEXT)) {
			startForeground(ONGOING_NOTIFICATION_ID, foregroundNotification)
		}
		this.foregroundNotification = foregroundNotification
	}

	fun combinedCallback() {
		startServiceNotification(iDriveConnectionReceiver.brand, ChassisCode.fromCode(carInformationObserver.capabilities["vehicle.type"] ?: "Unknown"))

		synchronized(MainService::class.java) {
			handler.removeCallbacks(shutdownTimeout)
			if (iDriveConnectionReceiver.isConnected && securityAccess.isConnected()) {
				// make sure we are subscribed for an instance id
				if ((iDriveConnectionReceiver.instanceId ?: -1) <= 0) {
					iDriveConnectionReceiver.subscribe(applicationContext)
				}

				// record when we first see the connection
				if (connectionTime == null) {
					connectionTime = System.currentTimeMillis()
				}
				// wait until it's connected long enough
				if (System.currentTimeMillis() - (connectionTime ?: 0) < STARTUP_DEBOUNCE) {
					handler.removeCallbacks(combinedCallbackRunnable)
					handler.postDelayed(combinedCallbackRunnable, 1000)
					return
				}

				var startAny = false

				AppSettings.loadSettings(applicationContext)

				// set the car app languages
				val locale = if (appSettings[AppSettings.KEYS.FORCE_CAR_LANGUAGE].isNotBlank()) {
					Locale.forLanguageTag(appSettings[AppSettings.KEYS.FORCE_CAR_LANGUAGE])
				} else if (appSettings[AppSettings.KEYS.PREFER_CAR_LANGUAGE].toBoolean() &&
							carInformationObserver.cdsData[CDS.VEHICLE.LANGUAGE] != null) {
					CDSVehicleLanguage.fromCdsProperty(carInformationObserver.cdsData[CDS.VEHICLE.LANGUAGE]).locale
				} else {
					null
				}
				L.loadResources(applicationContext, locale)

				// report car capabilities
				// also loads the car language
				startAny = startAny or startCarCapabilities()

				if (appSettings[AppSettings.KEYS.PREFER_CAR_LANGUAGE].toBoolean() &&
						carInformationObserver.cdsData[CDS.VEHICLE.LANGUAGE] == null) {
					// still waiting for language
					Log.d(TAG, "Waiting for the car's language to be confirmed")
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

					// start addons
					startAny = startAny or startAddons()

					backgroundInterruptionDetection.start()
				}
			} else {
				Log.d(TAG, "Not fully connected: IDrive:${iDriveConnectionReceiver.isConnected} SecurityService:${securityAccess.isConnected()}")
				if (connectionTime != null) {
					// record that we successfully disconnected
					backgroundInterruptionDetection.safelyStop()

					// show a donation popup, if it's time
					DonationRequest(this).countUsage()
				}
				connectionTime = null
				stopCarApps()
				val timeout = if (btStatus.isBTConnected) CONNECTED_PROBE_TIMEOUT else DISCONNECTED_PROBE_TIMEOUT
				handler.postDelayed(shutdownTimeout, timeout)
				handler.post(btfetchUuidsWithSdp)
			}
		}
		carInformationUpdater.isConnected = iDriveConnectionReceiver.isConnected && securityAccess.isConnected()
	}

	fun startModuleService(cls: Class<out Service>) {
		val connectionIntent = Intent(this, cls)
		connectionIntent.putExtra("EXTRA_BRAND", iDriveConnectionReceiver.brand)
		connectionIntent.putExtra("EXTRA_HOST", iDriveConnectionReceiver.host)
		connectionIntent.putExtra("EXTRA_PORT", iDriveConnectionReceiver.port)
		connectionIntent.putExtra("EXTRA_INSTANCE_ID", iDriveConnectionReceiver.instanceId)

		synchronized(this) {
			if (!moduleServiceBindings.containsKey(cls)) {
				val serviceConnection = ModuleServiceConnection(cls.simpleName)
				bindService(connectionIntent, serviceConnection, Context.BIND_AUTO_CREATE)
				moduleServiceBindings[cls] = serviceConnection
			} else {
				startService(connectionIntent)
			}
		}
	}

	fun stopModuleService(cls: Class<out Service>) {
		val connectionIntent = Intent(this, cls)
		connectionIntent.putExtra("EXTRA_BRAND", iDriveConnectionReceiver.brand)
		connectionIntent.putExtra("EXTRA_HOST", iDriveConnectionReceiver.host)
		connectionIntent.putExtra("EXTRA_PORT", iDriveConnectionReceiver.port)
		connectionIntent.putExtra("EXTRA_INSTANCE_ID", iDriveConnectionReceiver.instanceId)

		synchronized(this) {
			val connection = moduleServiceBindings.remove(cls)
			if (connection != null) {
				unbindService(connection)
			}
			stopService(connectionIntent)
		}
	}

	fun startCarCapabilities(): Boolean {
		startModuleService(CarInformationDiscoveryService::class.java)
		return true
	}

	fun stopCarCapabilities() {
		stopModuleService(CarInformationDiscoveryService::class.java)
	}

	fun startNotifications(): Boolean {
		val shouldRun = appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS].toBoolean()
		if (shouldRun) {
			startModuleService(NotificationAppService::class.java)
		} else {
			stopModuleService(NotificationAppService::class.java)
		}
		return shouldRun
	}

	fun stopNotifications() {
		stopModuleService(NotificationAppService::class.java)
	}

	fun startMaps(): Boolean {
		val shouldRun = appSettings[AppSettings.KEYS.ENABLED_GMAPS].toBoolean()
		if (shouldRun) {
			startModuleService(MapAppService::class.java)
		} else {
			stopModuleService(MapAppService::class.java)
		}
		return shouldRun
	}

	fun stopMaps() {
		stopModuleService(MapAppService::class.java)
	}

	fun startMusic(): Boolean {
		startModuleService(MusicAppService::class.java)
		return true
	}
	fun stopMusic() {
		stopModuleService(MusicAppService::class.java)
	}

	fun startAssistant(): Boolean {
		startModuleService(AssistantAppService::class.java)
		return true
	}

	fun stopAssistant() {
		stopModuleService(AssistantAppService::class.java)
	}

	fun startNavigationListener() {
		if (carInformationObserver.capabilities["navi"] == "true") {
			if (iDriveConnectionReceiver.brand?.lowercase(Locale.ROOT) == "bmw") {
				packageManager.setComponentEnabledSetting(
						ComponentName(BuildConfig.APPLICATION_ID, "${BuildConfig.APPLICATION_ID}.phoneui.NavActivityBMW"),
						PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
				)
			} else if (iDriveConnectionReceiver.brand?.lowercase(Locale.ROOT) == "mini") {
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

	fun startAddons(): Boolean {
		if (carInformationObserver.capabilities.isNotEmpty() && addonsService == null) {
			addonsService = AddonsService(applicationContext, iDriveConnectionReceiver, securityAccess)
		}
		return addonsService?.start() ?: false
	}

	fun stopAddons() {
		addonsService?.stop()
	}

	private fun stopCarApps() {
		stopCarCapabilities()
		stopNotifications()
		stopMaps()
		stopMusic()
		stopAssistant()
		stopNavigationListener()
		stopAddons()
	}

	/**
	 * Stop the service
	 */
	private fun handleActionStop() {
		Log.i(TAG, "Shutting down apps")
		synchronized(MainService::class.java) {
			stopCarApps()
		}
	}

	private fun stopServiceNotification() {
		Log.i(TAG, "Hiding foreground notification")
		stopForeground(true)
		foregroundNotification = null
	}

	/** Receive updates about the connection status */
	class ModuleServiceConnection(val name: String): ServiceConnection {
		var connected = false
			private set

		override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
			Log.i(TAG, "Successful connection to $name module")
			connected = true
		}
		override fun onNullBinding(name: ComponentName?) {
			Log.i(TAG, "Successful null connection to $name module")
			connected = true
		}
		override fun onServiceDisconnected(name: ComponentName?) {
			Log.i(TAG, "Disconnected from $name module")
			connected = false
		}
	}
}