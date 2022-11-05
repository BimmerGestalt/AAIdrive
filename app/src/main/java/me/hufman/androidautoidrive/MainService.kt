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
import me.hufman.androidautoidrive.carapp.carinfo.CarInformationDiscoveryService
import me.hufman.androidautoidrive.connections.BtStatus
import me.hufman.androidautoidrive.phoneui.DonationRequest
import me.hufman.androidautoidrive.phoneui.NavHostActivity
import java.util.*

class MainService: Service() {
	companion object {
		const val TAG = "MainService"

		const val ACTION_START = "me.hufman.androidautoidrive.MainService.start"
		const val ACTION_STOP = "me.hufman.androidautoidrive.MainService.stop"
		const val ACTION_SERVICE_MODULE = "me.hufman.androidautoidrive.carconnection.service"
		const val EXTRA_FOREGROUND = "EXTRA_FOREGROUND"

		const val CONNECTED_PROBE_TIMEOUT: Long = 2 * 60 * 1000     // if Bluetooth is connected
		const val DISCONNECTED_PROBE_TIMEOUT: Long = 20 * 1000      // if Bluetooth is not connected
		const val STARTUP_DEBOUNCE = 1500
		const val SERVICE_START_DEBOUNCE = 1500
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

	val moduleServiceTimes = HashMap<String, Long>()
	val moduleServiceBindings = HashMap<String, ServiceConnection>()
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
	var shutdownDeferredOnce = false        // whether we rescheduled shutdown after noticing Bluetooth still connected
	val shutdownTimeout = Runnable {
		if (!iDriveConnectionReceiver.isConnected || !securityAccess.isConnected()) {
			if (btStatus.isBTConnected && !shutdownDeferredOnce) {
				shutdownDeferredOnce = true
				scheduleShutdownTimeout()
			} else {
				stopSelf()
			}
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

		// show the notification, so we can be startForegroundService'd
		createNotificationChannel()
		startServiceNotification(iDriveConnectionReceiver.brand,
				ChassisCode.fromCode(carInformationObserver.capabilities["vehicle.type"] ?: "Unknown"),
				true)

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
		// try connecting to the security service
		if (!securityServiceThread.isAlive) {
			securityServiceThread.start()
		}
		securityServiceThread.connect()
		// start up car connection listener
		announceCarAPI()
		iDriveConnectionReceiver.subscribe(applicationContext)
		startCarProber()
		combinedCallback()
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

	private fun startServiceNotification(brand: String?, chassisCode: ChassisCode?, force: Boolean) {
		val notifyIntent = Intent(applicationContext, NavHostActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
		}
		val foregroundNotificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
				.setOngoing(true)
				.setContentTitle(getText(R.string.notification_title))
				.setContentText(getText(R.string.notification_description))
				.setSmallIcon(R.drawable.ic_notify)
				.setPriority(NotificationCompat.PRIORITY_LOW)
				.setContentIntent(PendingIntent.getActivity(applicationContext, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

		if (!iDriveConnectionReceiver.isConnected) {
			// show a notification even if we aren't connected, in case we were called with startForegroundService
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
		if (force ||
				this.foregroundNotification?.extras?.getCharSequence(Notification.EXTRA_TEXT) !=
				foregroundNotification.extras?.getCharSequence(Notification.EXTRA_TEXT)) {
			Log.i(TAG, "Creating foreground notification")
			startForeground(ONGOING_NOTIFICATION_ID, foregroundNotification)
			this.foregroundNotification = foregroundNotification
		}
	}

	fun combinedCallback() {
		synchronized(MainService::class.java) {
			handler.removeCallbacks(shutdownTimeout)
			startServiceNotification(iDriveConnectionReceiver.brand,
					ChassisCode.fromCode(carInformationObserver.capabilities["vehicle.type"] ?: "Unknown"),
					false)
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
				startCarCapabilities()

				if (appSettings[AppSettings.KEYS.PREFER_CAR_LANGUAGE].toBoolean() &&
						carInformationObserver.cdsData[CDS.VEHICLE.LANGUAGE] == null) {
					// still waiting for language
					Log.d(TAG, "Waiting for the car's language to be confirmed")
				} else {
					startModuleServices()

					// start navigation handler
					startNavigationListener()

					// start addons
					startAddons()

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
				scheduleShutdownTimeout()
				handler.post(btfetchUuidsWithSdp)
			}
			carInformationUpdater.isConnected = iDriveConnectionReceiver.isConnected && securityAccess.isConnected()
		}
	}

	fun scheduleShutdownTimeout() {
		val timeout = if (btStatus.isBTConnected) CONNECTED_PROBE_TIMEOUT else DISCONNECTED_PROBE_TIMEOUT
		handler.removeCallbacks(shutdownTimeout)
		handler.postDelayed(shutdownTimeout, timeout)
	}

	fun startModuleService(clsName: String) {
		val connectionIntent = Intent(ACTION_SERVICE_MODULE)
				.setComponent(ComponentName(applicationContext.packageName, clsName))
		connectionIntent.putExtra("EXTRA_BRAND", iDriveConnectionReceiver.brand)
		connectionIntent.putExtra("EXTRA_HOST", iDriveConnectionReceiver.host)
		connectionIntent.putExtra("EXTRA_PORT", iDriveConnectionReceiver.port)
		connectionIntent.putExtra("EXTRA_INSTANCE_ID", iDriveConnectionReceiver.instanceId)

		synchronized(this) {
			try {
				if (!moduleServiceBindings.containsKey(clsName)) {
					val serviceConnection = ModuleServiceConnection(clsName)
					bindService(connectionIntent, serviceConnection, Context.BIND_AUTO_CREATE)
					moduleServiceBindings[clsName] = serviceConnection
					moduleServiceTimes[clsName] = System.currentTimeMillis()
				} else {
					// Android seems to get cranky if we start the same service too often
					// usually happens with the CarInformationDiscoveryService
					if ((moduleServiceTimes[clsName] ?: 0) + SERVICE_START_DEBOUNCE < System.currentTimeMillis() ) {
						startService(connectionIntent)
						moduleServiceTimes[clsName] = System.currentTimeMillis()
					}
				}
			} catch (e: Exception) {
				// try again later
				Log.d(TAG, "Error while starting module service $clsName: $e")
				Unit        // so we aren't using the Log.d return code in the try/if blocks
			}
		}
	}

	fun stopModuleService(clsName: String) {
		val connectionIntent = Intent(ACTION_SERVICE_MODULE)
				.setComponent(ComponentName(applicationContext.packageName, clsName))
		connectionIntent.putExtra("EXTRA_BRAND", iDriveConnectionReceiver.brand)
		connectionIntent.putExtra("EXTRA_HOST", iDriveConnectionReceiver.host)
		connectionIntent.putExtra("EXTRA_PORT", iDriveConnectionReceiver.port)
		connectionIntent.putExtra("EXTRA_INSTANCE_ID", iDriveConnectionReceiver.instanceId)

		synchronized(this) {
			moduleServiceTimes.remove(clsName)
			val connection = moduleServiceBindings.remove(clsName)
			if (connection != null) {
				unbindService(connection)
			}
			stopService(connectionIntent)
		}
	}

	fun startModuleServices() {
		val intentService = Intent(ACTION_SERVICE_MODULE)
				.setPackage(applicationContext.packageName)
		packageManager.queryIntentServices(intentService, 0).forEach { resolveInfo ->
			startModuleService(resolveInfo.serviceInfo.name)
		}
	}

	fun stopModuleServices() {
		val intentService = Intent(ACTION_SERVICE_MODULE)
				.setPackage(applicationContext.packageName)
		packageManager.queryIntentServices(intentService, 0).forEach { resolveInfo ->
			stopModuleService(resolveInfo.serviceInfo.name)
		}
	}
	fun startCarCapabilities() {
		startModuleService(CarInformationDiscoveryService::class.java.name)
	}
	fun stopCarCapabilities() {
		stopModuleService(CarInformationDiscoveryService::class.java.name)
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
		stopModuleServices()
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