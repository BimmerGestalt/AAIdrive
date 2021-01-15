package me.hufman.androidautoidrive

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import me.hufman.androidautoidrive.carapp.maps.*
import me.hufman.idriveconnectionkit.android.IDriveConnectionStatus
import me.hufman.idriveconnectionkit.android.security.SecurityAccess

class MapService(val context: Context, val iDriveConnectionStatus: IDriveConnectionStatus, val securityAccess: SecurityAccess, val mapAppMode: MapAppMode) {
	var threadGMaps: CarThread? = null
	var mapApp: MapApp? = null
	var mapScreenCapture: VirtualDisplayScreenCapture? = null
	var virtualDisplay: VirtualDisplay? = null
	var mapController: GMapsController? = null
	var mapListener: MapsInteractionControllerListener? = null

	fun start(): Boolean {
		if (AppSettings[AppSettings.KEYS.ENABLED_GMAPS].toBoolean() &&
				ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
				== PackageManager.PERMISSION_GRANTED) {
			synchronized(this) {
				if (threadGMaps == null) {
					threadGMaps = CarThread("GMaps") {
						Log.i(MainService.TAG, "Starting GMaps")
						val mapScreenCapture = VirtualDisplayScreenCapture.build(mapAppMode.fullDimensions.visibleWidth, mapAppMode.fullDimensions.visibleHeight)
						this.mapScreenCapture = mapScreenCapture
						val virtualDisplay = VirtualDisplayScreenCapture.createVirtualDisplay(context, mapScreenCapture.imageCapture, 250)
						this.virtualDisplay = virtualDisplay
						val mapController = GMapsController(context, MapResultsSender(context), virtualDisplay, MutableAppSettingsReceiver(context, null /* specifically main thread */))
						this.mapController = mapController
						val mapListener = MapsInteractionControllerListener(context, mapController)
						mapListener.onCreate()
						this.mapListener = mapListener

						val mapApp = MapApp(iDriveConnectionStatus, securityAccess,
								CarAppAssetManager(context, "smartthings"),
								mapAppMode,
								MapInteractionControllerIntent(context), mapScreenCapture)
						this.mapApp = mapApp
						val handler = threadGMaps?.handler
						if (handler != null) {
							mapApp.onCreate(context, handler)
						}
					}
					threadGMaps?.start()
				}
			}
			return true
		} else {
			if (threadGMaps != null) {
				Log.i(MainService.TAG, "GMaps app needs to be shut down...")
				stop()
			}
			return false
		}
	}

	fun stop() {
		mapScreenCapture?.onDestroy()
		virtualDisplay?.release()
		// nothing to stop in mapController
		mapListener?.onDestroy()
		mapApp?.onDestroy(context)

		mapScreenCapture = null
		virtualDisplay = null
		mapController = null
		mapListener = null

		// if we caught it during initialization, kill it again
		val thread = threadGMaps
		if (thread?.isAlive == true) {
			thread.post {
				stop()
				mapApp = null
			}
		} else {
			mapApp = null
		}
		threadGMaps?.quitSafely()
		threadGMaps = null
	}
}