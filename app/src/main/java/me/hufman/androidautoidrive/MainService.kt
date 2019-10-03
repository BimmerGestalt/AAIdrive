package me.hufman.androidautoidrive

import android.app.*
import android.app.Notification.PRIORITY_LOW
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.bmwgroup.connected.car.app.BrandType
import me.hufman.androidautoidrive.carapp.notifications.CarNotificationControllerIntent
import me.hufman.androidautoidrive.carapp.notifications.NotificationListenerServiceImpl
import me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications
import me.hufman.idriveconnectionkit.android.CarAPIAppInfo
import me.hufman.idriveconnectionkit.android.CarAPIDiscovery
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.SecurityService

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

	val idriveConnectionListener = IDriveConnectionListener()   // start listening to car connection, if the AndroidManifest listener didn't start
	val carProberThread by lazy {
		CarProber(
			CarAppAssetManager(this, "smartthings").getAppCertificateRaw("bmw")!!.readBytes(),
			CarAppAssetManager(this, "smartthings").getAppCertificateRaw("mini")!!.readBytes()
		)
	}

	val securityServiceThread = SecurityServiceThread(this)

	var threadCapabilities: CarThread? = null
	var carappCapabilities: CarInformationDiscovery? = null

	var threadNotifications: CarThread? = null
	var carappNotifications: PhoneNotifications? = null

	var mapService = MapService(this)

	var musicService = MusicService(this)


	override fun onBind(intent: Intent?): IBinder? {
		return null
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		Analytics.init(this)

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
		idriveConnectionListener.unsubscribe(this)
		carProberThread.quitSafely()
		super.onDestroy()
	}

	/**
	 * Start the service
	 */
	private fun handleActionStart() {
		Log.i(TAG, "Starting up service")
		createNotificationChannel()
		// try connecting to the security service
		if (!securityServiceThread.isAlive) {
			securityServiceThread.start()
		}
		securityServiceThread.connect()
		SecurityService.subscribe(Runnable {
			combinedCallback()
		})
		IDriveConnectionListener.callback = Runnable {
			combinedCallback()
		}
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

	private fun startServiceNotification(brand: String?) {
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
		foregroundNotification = foregroundNotificationBuilder.build()
		startForeground(ONGOING_NOTIFICATION_ID, foregroundNotification)
	}

	fun combinedCallback() {
		synchronized(MainService::class.java) {
			if (IDriveConnectionListener.isConnected && SecurityService.isConnected()) {
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

				// check if we are idle and should shut down
				if (startAny ){
					startServiceNotification(IDriveConnectionListener.brand)
				} else {
					Log.i(TAG, "No apps are enabled, skipping the service start")
					stopServiceNotification()
					stopSelf()
				}
			} else {
				Log.d(TAG, "Not fully connected: IDrive:${IDriveConnectionListener.isConnected} SecurityService:${SecurityService.isConnected()}")

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

					carappCapabilities = CarInformationDiscovery(CarAppAssetManager(this, "smartthings"))
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
						carappNotifications = PhoneNotifications(CarAppAssetManager(this, "basecoreOnlineServices"),
								PhoneAppResourcesAndroid(this),
								CarNotificationControllerIntent(this))
						carappNotifications?.onCreate(this, threadNotifications?.handler)
						// request an initial draw
						sendBroadcast(Intent(NotificationListenerServiceImpl.INTENT_REQUEST_DATA))
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

	private fun stopCarApps() {
		stopCarCapabilities()
		stopNotifications()
		stopMaps()
		stopMusic()
		stopServiceNotification()
	}

	/**
	 * Stop the service
	 */
	private fun handleActionStop() {
		Log.i(TAG, "Shutting down service")
		synchronized(MainService::class.java) {
			stopCarApps()
			SecurityService.listener = Runnable {}
			securityServiceThread.disconnect()
		}
	}

	private fun stopServiceNotification() {
		Log.i(TAG, "Hiding foreground notification")
		stopForeground(true)
	}
}