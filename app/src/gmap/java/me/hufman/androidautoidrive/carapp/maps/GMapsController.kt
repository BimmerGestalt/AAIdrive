package me.hufman.androidautoidrive.carapp.maps

import android.content.Context
import android.hardware.display.VirtualDisplay
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import me.hufman.androidautoidrive.AppSettingsObserver
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.maps.CarLocationProvider
import me.hufman.androidautoidrive.maps.LatLong
import kotlin.math.max
import kotlin.math.min

class GMapsController(private val context: Context,
                      private val carLocationProvider: CarLocationProvider,
                      private val virtualDisplay: VirtualDisplay,
                      private val appSettings: AppSettingsObserver,
                      private val mapAppMode: MapAppMode): MapInteractionController {
	val TAG = "GMapsController"
	var handler = Handler(context.mainLooper)
	var projection: GMapsProjection? = null

	private val SHUTDOWN_WAIT_INTERVAL = 120000L   // milliseconds of inactivity before shutting down map

	private var lastSettingsTime = 0L   // the last time we checked settings, for day/night check
	private val SETTINGS_TIME_INTERVAL = 5 * 60000  // milliseconds between checking day/night

	val navController = GMapsNavController.getInstance(context, carLocationProvider) {
		drawNavigation()
		mapAppMode.currentNavDestination = it.currentNavDestination
	}
	val gMapLocationSource = GMapsLocationSource()
	var currentLocation: Location? = null

	var animatingCamera = false
	var zoomingCamera = false   // whether the animation started with a zoom command, and thus should be cancelable
	val animationFinishedCallback = object: GoogleMap.CancelableCallback {
		override fun onFinish() {
			animatingCamera = false
			zoomingCamera = false
			startZoom = currentZoom // restore a backgrounded map to this zoom level
		}
		override fun onCancel() {
			animatingCamera = false
		}
	}
	private var startZoom = 6f  // what zoom level we start the projection with
	private var currentZoom = 15f

	init {
		carLocationProvider.callback = { location ->
			handler.post {
				onLocationUpdate(location)
			}
		}
	}

	override fun showMap() {
		Log.i(TAG, "Beginning map projection")

		// cancel a shutdown timer
		handler.removeCallbacks(shutdownMapRunnable)

		if (projection == null) {
			Log.i(TAG, "First showing of the map")
			val projection = GMapsProjection(context, virtualDisplay.display, appSettings, gMapLocationSource)
			this.projection = projection
			projection.mapListener = Runnable {
				// when getMapAsync finishes
				initCamera()
				drawNavigation()    // restore navigation, if it's still going
			}

		}
		if (projection?.isShowing == false) {
			projection?.show()
		}
		// nudge the camera to trigger a redraw, in case we changed windows
		if (!animatingCamera) {
			projection?.map?.moveCamera(CameraUpdateFactory.scrollBy(1f, 1f))
		}

		// register for location updates
		carLocationProvider.start()
	}

	override fun pauseMap() {
		carLocationProvider.stop()

		handler.postDelayed(shutdownMapRunnable, SHUTDOWN_WAIT_INTERVAL)
	}

	private val shutdownMapRunnable = Runnable {
		Log.i(TAG, "Shutting down GMapProjection due to inactivity of ${SHUTDOWN_WAIT_INTERVAL}ms")
		projection?.hide()
		projection = null
	}

	private fun onLocationUpdate(location: Location) {
		if (currentLocation == null) {  // first view
			initCamera()
		}

		// save the new location and move the camera
		currentLocation = location
		updateCamera()

		// move the map dot to the new location
		gMapLocationSource.onLocationUpdate(location)

		// check to re-apply day/night settings after an interval
		if (lastSettingsTime + SETTINGS_TIME_INTERVAL < System.currentTimeMillis()) {
			projection?.applySettings()
			lastSettingsTime = System.currentTimeMillis()
		}
	}

	override fun zoomIn(steps: Int) {
		Log.i(TAG, "Zooming map in $steps steps")
		mapAppMode.startInteraction()
		zoomingCamera = true
		currentZoom = min(18f, currentZoom + steps)
		updateCamera()
	}
	override fun zoomOut(steps: Int) {
		Log.i(TAG, "Zooming map out $steps steps")
		mapAppMode.startInteraction()
		zoomingCamera = true
		currentZoom = max(0f, currentZoom - steps)
		updateCamera()
	}

	private fun initCamera() {
		// set the camera to the starting position
		mapAppMode.startInteraction()
		val location = currentLocation
		if (location != null) {
			val cameraLocation = LatLng(location.latitude, location.longitude)
			projection?.map?.moveCamera(CameraUpdateFactory.newLatLngZoom(cameraLocation, startZoom))
		} else {
			projection?.map?.moveCamera(CameraUpdateFactory.zoomTo(startZoom))
		}
	}

	private fun updateCamera() {
		val location = currentLocation ?: return
		if (!animatingCamera || zoomingCamera) {
			// if the camera is idle or we are zooming the camera already
			val cameraLocation = LatLng(location.latitude, location.longitude)
			projection?.map?.stopAnimation()
			projection?.map?.animateCamera(CameraUpdateFactory.newLatLngZoom(cameraLocation, currentZoom), animationFinishedCallback)
			animatingCamera = true
		}
	}

	override fun navigateTo(dest: LatLong) {
		Log.i(TAG, "Beginning navigation to $dest")
		mapAppMode.startInteraction(NAVIGATION_MAP_STARTZOOM_TIME + 4000)
		// clear out previous nav
		projection?.map?.clear()
		// show new nav destination icon
		navController.navigateTo(dest)

		// start zoom animation
		animateNavigation()
	}

	private fun animateNavigation() {
		// show a camera animation to zoom out to the whole navigation route
		val dest = navController.currentNavDestination ?: return
		val lastLocation = currentLocation ?: return
		animatingCamera = true
		// zoom out to the full view
		val startLatLng = LatLng(lastLocation.latitude, lastLocation.longitude)
		val destLatLng = LatLng(dest.latitude, dest.longitude)

		val navigationBounds = LatLngBounds.builder()
				.include(startLatLng)
				.include(destLatLng)
				.build()
		val currentVisibleRegion = projection?.map?.projection?.visibleRegion?.latLngBounds
		if (currentVisibleRegion == null || !currentVisibleRegion.contains(navigationBounds.northeast) || !currentVisibleRegion.contains(navigationBounds.southwest)) {
			handler.postDelayed({
				projection?.map?.animateCamera(CameraUpdateFactory.newLatLngBounds(navigationBounds, NAVIGATION_MAP_STARTZOOM_PADDING))
			}, 100)
		}

		// then zoom back in to the user's chosen zoom
		handler.postDelayed({
			projection?.map?.animateCamera(CameraUpdateFactory.newLatLngZoom(startLatLng, currentZoom))
			animatingCamera = false
		}, NAVIGATION_MAP_STARTZOOM_TIME.toLong())
	}

	private fun drawNavigation() {
		// make sure we are in the UI thread, and then draw navigation lines onto it
		// because route search comes back on a network thread
		val action = {
			val map = projection?.map
			if (map != null) {
				map.clear()

				// destination flag
				val dest = navController.currentNavDestination
				if (dest != null) {
					val destLatLng = LatLng(dest.latitude, dest.longitude)
					val marker = MarkerOptions()
							.position(destLatLng)
							.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
							.visible(true)
					map.addMarker(marker)
				}

				// routing
				val currentNavRoute = navController.currentNavRoute
				if (currentNavRoute != null) {
					map.addPolyline(PolylineOptions().color(context.getColor(R.color.mapRouteLine)).addAll(currentNavRoute))
				}
			}
		}
		if (Looper.myLooper() != handler.looper) {
			handler.post(action)
		} else {
			action()
		}
	}

	override fun recalcNavigation() {
		navController.currentNavDestination?.let {
			navController.navigateTo(it)
		}
	}

	override fun stopNavigation() {
		// clear out previous nav
		navController.stopNavigation()
	}
}