package me.hufman.androidautoidrive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageWriter
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.bimmergestalt.idriveconnectkit.CDSProperty
import io.bimmergestalt.idriveconnectkit.SubsetRHMIDimensions
import me.hufman.androidautoidrive.carapp.maps.MapAppMode
import me.hufman.androidautoidrive.carapp.maps.MapboxController
import me.hufman.androidautoidrive.carapp.maps.MapsInteractionControllerListener
import me.hufman.androidautoidrive.carapp.maps.VirtualDisplayScreenCapture
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.cds.CDSDataProvider
import me.hufman.androidautoidrive.maps.CdsLocationProvider

class MapTestingService: Service() {
	companion object {
		const val TAG = "MapTestingService"
		private val ONGOING_NOTIFICATION_ID = 20524
		private val NOTIFICATION_CHANNEL_ID = "MapTestingNotification"

		const val ACTION_START = "me.hufman.androidautoidrive.MapTestingService.start"
		const val ACTION_PAUSE = "me.hufman.androidautoidrive.MapTestingService.pause"
		const val ACTION_STOP = "me.hufman.androidautoidrive.MapTestingService.stop"
		const val EXTRA_SURFACE = "me.hufman.androidautoidrive.MapTestingService.SURFACE"
		const val EXTRA_WIDTH = "me.hufman.androidautoidrive.MapTestingService.WIDTH"
		const val EXTRA_HEIGHT = "me.hufman.androidautoidrive.MapTestingService.HEIGHT"

		fun start(context: Context, surface: Surface, width: Int, height: Int) {
			val intent = Intent(context, MapTestingService::class.java).apply {
				action = ACTION_START
				putExtra(EXTRA_SURFACE, surface)
				putExtra(EXTRA_WIDTH, width)
				putExtra(EXTRA_HEIGHT, height)
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				context.startForegroundService(intent)
			} else {
				context.startService(intent)
			}
		}

		fun pause(context: Context) {
			context.startService(Intent(context, MapTestingService::class.java).setAction(ACTION_PAUSE))
		}
	}

	private var notification: Notification? = null
	private var mapScreenCapture: VirtualDisplayScreenCapture? = null
	private var virtualDisplay: VirtualDisplay? = null
	private var mapController: MapboxController? = null
	private var mapListener: MapsInteractionControllerListener? = null

	override fun onBind(p0: Intent?): IBinder? {
		return null;
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		Log.i(TAG, "Starting MapTestingService with ${intent?.action}")
		if (intent?.action == ACTION_START) {
			createNotificationChannel()
			createServiceNotification()
			if (this.virtualDisplay == null) {
				createMap(
					intent.getIntExtra(EXTRA_WIDTH, 700),
					intent.getIntExtra(EXTRA_HEIGHT, 480),
				)
			}
			startMap(
				intent.getParcelableExtra<Surface>(EXTRA_SURFACE) as Surface,
				intent.getIntExtra(EXTRA_WIDTH, 700),
				intent.getIntExtra(EXTRA_HEIGHT, 480),
			)
		} else if (intent?.action == ACTION_PAUSE) {
			pauseMap()
		} else if (intent?.action == ACTION_STOP) {
			stopServiceAndNotification()
		}

		return START_STICKY
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
				getString(R.string.notification_channel_map_testing),
				NotificationManager.IMPORTANCE_MIN)

			val notificationManager = getSystemService(NotificationManager::class.java)
			notificationManager.createNotificationChannel(channel)
		}
	}

	private fun createServiceNotification() {
		val notifyIntent = Intent(applicationContext, MapTestingService::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
		}
		val shutdownIntent = Intent(applicationContext, MapTestingService::class.java).apply {
			action = ACTION_STOP
		}
		val shutdownAction = NotificationCompat.Action.Builder(null, getString(R.string.notification_shutdown),
			PendingIntent.getService(this, 41, shutdownIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		).build()
		val foregroundNotificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
			.setOngoing(true)
			.setContentTitle(getText(R.string.notification_title))
			.setContentText(getText(R.string.lbl_map_testing))
			.setSmallIcon(R.drawable.ic_notify)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.setContentIntent(PendingIntent.getActivity(applicationContext, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
			.addAction(shutdownAction)

		var foregroundNotification = this.notification
		if (foregroundNotification == null) {
			foregroundNotification = foregroundNotificationBuilder.build()
			this.notification = foregroundNotification
		}
		startForegroundService(ONGOING_NOTIFICATION_ID, foregroundNotification)
	}

	fun startForegroundService(id: Int, notification: Notification) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			val flags = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
			try {
				super.startForeground(id, notification, flags)
			} catch (e: Exception) {
				Log.e(TAG, "Failed to startForeground", e)
				stopSelf()      // not allowed at this time, for some reason
			}
		} else {
			super.startForeground(id, notification)
		}
	}

	private fun stopServiceAndNotification() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			stopForeground(STOP_FOREGROUND_REMOVE)
		} else {
			@Suppress("DEPRECATION")
			stopForeground(true)
		}
	}

	private fun createMap(width: Int, height: Int) {
		val appSettingsObserver = MutableAppSettingsReceiver(applicationContext, null /* specifically main thread */)
		val cdsData = CDSDataProvider()
		cdsData.setConnection(CarInformation.cdsData.asConnection(cdsData))
		CarInformation.cachedCdsData[CDSProperty.NAVIGATION_GPSPOSITION]?.let {
			CarInformation.cdsData.onPropertyChangedEvent(CDSProperty.NAVIGATION_GPSPOSITION, it)
		}
		CarInformation.cachedCdsData[CDSProperty.NAVIGATION_GPSEXTENDEDINFO]?.let {
			CarInformation.cdsData.onPropertyChangedEvent(CDSProperty.NAVIGATION_GPSEXTENDEDINFO, it)
		}
		val carLocationProvider = CdsLocationProvider(cdsData, false)
		val mapAppMode = MapAppMode.build(SubsetRHMIDimensions(width, height), appSettingsObserver, cdsData, MusicAppMode.TRANSPORT_PORTS.USB)
		val mapScreenCapture = VirtualDisplayScreenCapture.build(mapAppMode)
		this.mapScreenCapture = mapScreenCapture
		val virtualDisplay = VirtualDisplayScreenCapture.createVirtualDisplay(applicationContext, mapScreenCapture.imageCapture, 250)
		this.virtualDisplay = virtualDisplay
		val mapController = MapboxController(applicationContext, carLocationProvider, virtualDisplay, appSettingsObserver, mapAppMode)
		this.mapController = mapController
		val mapListener = MapsInteractionControllerListener(applicationContext, mapController)
		this.mapListener = mapListener
		mapListener.onCreate()
	}

	private fun startMap(outputSurface: Surface, width: Int, height: Int) {
		Log.i(TAG, "Starting map with $outputSurface ${width}x${height} from $mapScreenCapture")
		try {
			val imageWriter = ImageWriter.newInstance(outputSurface, 1)
			mapScreenCapture?.changeImageSize(width, height)
			mapScreenCapture?.registerImageListener {
				val mapScreenCapture = mapScreenCapture
				if (mapScreenCapture != null) {
					transferScreenCapture(mapScreenCapture, imageWriter)
				}
			}
			mapScreenCapture?.also {
				transferScreenCapture(it, imageWriter)
			}
		} catch (e: Exception) {
			Log.e(TAG, "Failed to start map", e)
		}
		mapController?.showMap()
	}

	private fun pauseMap() {
		mapController?.pauseMap()
	}

	private fun transferScreenCapture(screenCapture: VirtualDisplayScreenCapture, imageWriter: ImageWriter) {
		var outputImage: Image? = null
		try {
			outputImage = imageWriter.dequeueInputImage()
			val bitmap = screenCapture.getFrame()
			bitmap?.copyPixelsToBuffer(outputImage.planes[0].buffer)
			imageWriter.queueInputImage(outputImage)
		} catch (e: Exception) {
			// Output surface disappeared
//			Log.w(TAG, "Failed to copy buffer", e)
			outputImage?.close()
		}
	}

	override fun onDestroy() {
		super.onDestroy()

		notification = null
		mapScreenCapture?.onDestroy()
		virtualDisplay?.release()
		mapListener?.onDestroy()
	}
}