package me.hufman.androidautoidrive.carapp.maps

import android.content.Context
import android.hardware.display.VirtualDisplay
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.ResourceOptionsManager
import com.mapbox.maps.plugin.animation.camera
import me.hufman.androidautoidrive.AppSettingsObserver
import me.hufman.androidautoidrive.BuildConfig
import me.hufman.androidautoidrive.maps.CarLocationProvider
import me.hufman.androidautoidrive.maps.LatLong
import kotlin.math.max
import kotlin.math.min

class MapboxController(private val context: Context,
                       private val carLocationProvider: CarLocationProvider,
                       private val virtualDisplay: VirtualDisplay,
                       private val appSettings: AppSettingsObserver): MapInteractionController {

	private val SHUTDOWN_WAIT_INTERVAL = 120000L   // milliseconds of inactivity before shutting down map

	var handler = Handler(Looper.getMainLooper())
	var projection: MapboxProjection? = null
	private val mapboxLocationSource = MapboxLocationSource()
	var currentLocation: Location? = null
	private var startZoom = 6f  // what zoom level we start the projection with
	private var currentZoom = 15f

	init {
		ResourceOptionsManager.getDefault(context.applicationContext, BuildConfig.MapboxAccessToken)

		carLocationProvider.callback = { location ->
			handler.post {
				onLocationUpdate(location)
			}
		}
	}

	private fun onLocationUpdate(location: Location) {
		val firstView = currentLocation == null
		currentLocation = location
		// move the map dot to the new location
		mapboxLocationSource.onLocationUpdate(location)

		if (firstView) {  // first view
			initCamera()
		} else {
			updateCamera()
		}
	}

	override fun showMap() {
		if (projection == null) {
			Log.i(TAG, "First showing of the map")
			this.projection = MapboxProjection(context, virtualDisplay.display, appSettings, mapboxLocationSource)
		}

		if (projection?.isShowing == false) {
			projection?.show()
		}

		// register for location updates
		carLocationProvider.start()
	}

	override fun pauseMap() {
		carLocationProvider.stop()

		handler.postDelayed(shutdownMapRunnable, SHUTDOWN_WAIT_INTERVAL)
	}
	private val shutdownMapRunnable = Runnable {
		Log.i(TAG, "Shutting down MapboxProjection due to inactivity of ${SHUTDOWN_WAIT_INTERVAL}ms")
		projection?.hide()
		projection = null
	}

	override fun zoomIn(steps: Int) {
		currentZoom = min(20f, currentZoom + steps)
		updateCamera()
	}

	override fun zoomOut(steps: Int) {
		currentZoom = max(0f, currentZoom - steps)
		updateCamera()
	}

	private fun initCamera() {
		// set the camera to the starting position
		val location = currentLocation
		if (location != null) {
			val cameraPosition = CameraOptions.Builder()
					.center(Point.fromLngLat(location.longitude, location.latitude))
					.zoom(startZoom.toDouble())
					.build()
			projection?.map?.getMapboxMap()?.setCamera(cameraPosition)
		}
	}

	private fun updateCamera() {
		val location = currentLocation ?: return
		val cameraPosition = CameraOptions.Builder()
				.center(Point.fromLngLat(location.longitude, location.latitude))
				.zoom(currentZoom.toDouble())
				.build()
		projection?.map?.camera?.flyTo(cameraPosition)
	}

	override fun navigateTo(dest: LatLong) {
		TODO("Not yet implemented")
	}

	override fun stopNavigation() {
		TODO("Not yet implemented")
	}
}