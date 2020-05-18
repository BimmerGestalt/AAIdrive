package me.hufman.androidautoidrive

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.support.v4.content.ContextCompat
import android.util.Log
import me.hufman.androidautoidrive.carapp.maps.*

class MapService(val context: Context) {
	var threadGMaps: CarThread? = null
	var mapApp: MapApp? = null
	var mapScreenCapture: VirtualDisplayScreenCapture? = null
	var virtualDisplay: VirtualDisplay? = null
	var mapController: GMapsController? = null
	var mapListener: MapsInteractionControllerListener? = null

	companion object {
		fun createVirtualDisplay(context: Context, imageCapture: ImageReader, dpi:Int = 100): VirtualDisplay {
			val displayManager = context.getSystemService(DisplayManager::class.java)
			return displayManager.createVirtualDisplay("IDriveGoogleMaps",
					imageCapture.width, imageCapture.height, dpi,
					imageCapture.surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
					null, Handler(Looper.getMainLooper()))
		}

	}

	fun start(): Boolean {
		if (AppSettings[AppSettings.KEYS.ENABLED_GMAPS].toBoolean() &&
				ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
				== PackageManager.PERMISSION_GRANTED) {
			synchronized(this) {
				if (threadGMaps == null) {
					threadGMaps = CarThread("GMaps") {
						Log.i(MainService.TAG, "Starting GMaps")
						val mapScreenCapture = VirtualDisplayScreenCapture.build()
						this.mapScreenCapture = mapScreenCapture
						val virtualDisplay = createVirtualDisplay(context, mapScreenCapture.imageCapture, 100)
						this.virtualDisplay = virtualDisplay
						val mapController = GMapsController(context, MapResultsSender(context), virtualDisplay)
						this.mapController = mapController
						val mapListener = MapsInteractionControllerListener(context, mapController)
						mapListener.onCreate()
						this.mapListener = mapListener

						mapApp = MapApp(CarAppAssetManager(context, "smartthings"),
								MapInteractionControllerIntent(context), mapScreenCapture)
						val handler = threadGMaps?.handler
						if (handler != null) {
							mapApp?.onCreate(context, handler)
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
		threadGMaps?.handler?.post {
			mapApp?.onDestroy(context)
			mapListener?.onDestroy()
			mapScreenCapture?.onDestroy()
			virtualDisplay?.release()
			threadGMaps?.handler?.looper?.quitSafely()

			mapApp = null
			mapController = null
			mapListener = null
			mapScreenCapture = null
			virtualDisplay = null
		}

		threadGMaps = null
	}
}