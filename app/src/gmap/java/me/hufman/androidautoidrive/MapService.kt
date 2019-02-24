package me.hufman.androidautoidrive

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.support.v4.content.ContextCompat
import android.util.Log
import me.hufman.androidautoidrive.carapp.maps.*

class MapService(val context: Context) {
	var threadGMaps: CarThread? = null
	var mapView: MapView? = null
	var mapScreenCapture: VirtualDisplayScreenCapture? = null
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
						val mapScreenCapture = VirtualDisplayScreenCapture(context)
						this.mapScreenCapture = mapScreenCapture
						val mapController = GMapsController(context, MapResultsSender(context), mapScreenCapture)
						this.mapController = mapController
						val mapListener = MapsInteractionControllerListener(context, mapController)
						mapListener.onCreate()
						this.mapListener = mapListener

						mapView = MapView(CarAppAssetManager(context, "smartthings"),
								MapInteractionControllerIntent(context), mapScreenCapture)
						mapView?.onCreate(context, threadGMaps?.handler)
					}
					threadGMaps?.start()
				}
			}
			return true
		} else {
			Log.i(MainService.TAG, "GMaps app needs to be shut down...")
			stop()
			return false
		}
	}

	fun stop() {
		mapView?.onDestroy(context)
		mapListener?.onDestroy()
		mapScreenCapture?.onDestroy()
		threadGMaps?.handler?.looper?.quit()

		mapView = null
		mapController = null
		mapListener = null
		mapScreenCapture = null
		threadGMaps = null
	}
}