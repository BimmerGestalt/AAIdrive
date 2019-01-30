package me.hufman.androidautoidrive.carapp.maps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.support.v4.content.ContextCompat
import android.util.Log
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.places.AutocompleteFilter
import com.google.android.gms.location.places.AutocompletePredictionBufferResponse
import com.google.android.gms.location.places.Places
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.Task
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.PendingResult
import com.google.maps.model.DirectionsResult
import com.google.maps.model.TravelMode
import me.hufman.androidautoidrive.R
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class GMapsController(private val context: Context, private val resultsController: MapResultsController, private val screenCapture: VirtualDisplayScreenCapture): MapInteractionController {
	val TAG = "GMapsController"
	var handler = Handler(context.mainLooper)
	var projection: GMapsProjection? = null

	private val placesClient = Places.getGeoDataClient(context)!!
	private val geoClient = GeoApiContext().setQueryRateLimit(3)
			.setApiKey(context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
					.metaData.getString("com.google.android.geo.API_KEY"))
			.setConnectTimeout(2, TimeUnit.SECONDS)
			.setReadTimeout(2, TimeUnit.SECONDS)
			.setWriteTimeout(2, TimeUnit.SECONDS)

	val locationProvider = LocationServices.getFusedLocationProviderClient(context)!!
	val locationCallback = LocationCallbackImpl()
	var currentLocation: LatLng? = null

	var animatingCamera = false
	var zoomingCamera = false   // whether the animation started with a zoom command, and thus should be cancelable
	val animationFinishedCallback = object: GoogleMap.CancelableCallback {
		override fun onFinish() {
			animatingCamera = false
			zoomingCamera = false
		}
		override fun onCancel() {
			animatingCamera = false
		}
	}
	var currentZoom = 15f
	var currentSearchResults: Task<AutocompletePredictionBufferResponse>? = null
	var currentNavDestination: LatLong? = null
	var currentNavRoute: List<LatLng>? = null

	override fun showMap() {
		Log.i(TAG, "Beginning map projection")
		if (projection == null) {
			Log.i(TAG, "First showing of the map")
			val projection = GMapsProjection(context, screenCapture.virtualDisplay.display)
			this.projection = projection
		}
		if (projection?.isShowing == false) {
			projection?.show()
		}
		// nudge the camera to trigger a redraw, in case we changed windows
		if (!animatingCamera) {
			projection?.map?.moveCamera(CameraUpdateFactory.scrollBy(1f, 1f))
		}

		// register for location updates
		if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			val locationRequest = LocationRequest()
			locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
			locationRequest.interval = 3000
			locationRequest.fastestInterval = 500

			locationProvider.requestLocationUpdates(locationRequest, locationCallback, null)
		}
	}

	override fun pauseMap() {
//		projection?.hide()

		locationProvider.removeLocationUpdates(locationCallback)
	}

	inner class LocationCallbackImpl: LocationCallback() {
		override fun onLocationResult(location: LocationResult?) {
			if (location != null && location.lastLocation != null) {
				if (currentLocation == null) {  // first view
					val cameraLocation = LatLng(location.lastLocation.latitude, location.lastLocation.longitude)
					projection?.map?.moveCamera(CameraUpdateFactory.newLatLngZoom(cameraLocation, 6f))
				}

				currentLocation = LatLng(location.lastLocation.latitude, location.lastLocation.longitude)
				projection?.location = currentLocation
				projection?.applySettings()

				updateCamera()
			}
		}
	}

	override fun zoomIn(steps: Int) {
		Log.i(TAG, "Zooming map in $steps steps")
		zoomingCamera = true
		currentZoom = min(20f, currentZoom + steps)
		updateCamera()
	}
	override fun zoomOut(steps: Int) {
		Log.i(TAG, "Zooming map out $steps steps")
		zoomingCamera = true
		currentZoom = max(0f, currentZoom - steps)
		updateCamera()
	}
	private fun updateCamera() {
		if (!animatingCamera || zoomingCamera) {
			// if the camera is idle or we are zooming the camera already
			animatingCamera = true
			projection?.map?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, currentZoom), animationFinishedCallback)
		}
	}

	override fun searchLocations(query: String) {
		val bounds = projection?.map?.projection?.visibleRegion?.latLngBounds
		val resultsTask = placesClient.getAutocompletePredictions(query, bounds, AutocompleteFilter.Builder().build())
		currentSearchResults = resultsTask
		resultsTask.addOnCompleteListener {
			if (currentSearchResults == resultsTask && it.isSuccessful) {
				val results = it.result ?: return@addOnCompleteListener
				Log.i(TAG, "Received ${results.count} results for query $query")

				val mapResults = results.filter {
					it.placeId != null
				}.map {
					MapResult(it.placeId!!, it.getPrimaryText(null).toString(), null)
				}
				results.release()
				resultsController.onSearchResults(mapResults.toTypedArray())
			} else if (! it.isSuccessful) {
				Log.w(TAG, "Unsuccessful result when loading results for $query")
			}
		}
	}

	override fun resultInformation(resultId: String) {
		val resultTask = placesClient.getPlaceById(resultId)
		resultTask.addOnCompleteListener {
			if (it.isSuccessful && it.result?.count ?: 0 > 0) {
				Log.i(TAG, "Received ${it.result?.count} results")
				val results = it.result
				Log.i(TAG, "Fetched buffer response $results")
				val result = it.result?.get(0) ?: return@addOnCompleteListener
				val mapResult = MapResult(resultId, result.name.toString(), result.address.toString(),
						LatLong(result.latLng.latitude, result.latLng.longitude))
				it.result?.release()
				resultsController.onPlaceResult(mapResult)
			} else {
				Log.w(TAG, "Did not find Place info for Place ID $resultId")
			}
		}
	}

	override fun navigateTo(dest: LatLong) {
		Log.i(TAG, "Beginning navigation to $dest")
		// clear out previous nav
		projection?.map?.clear()
		// show new nav destination icon
		currentNavDestination = dest
		val destLatLng = LatLng(dest.latitude, dest.longitude)
		val marker = MarkerOptions()
				.position(destLatLng)
				.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
				.visible(true)
		projection?.map?.addMarker(marker)

		// start a route search
		val lastLocation = currentLocation ?: return
		val origin = com.google.maps.model.LatLng(lastLocation.latitude, lastLocation.longitude)
		val routeDest = com.google.maps.model.LatLng(dest.latitude, dest.longitude)
		val directionsRequest = DirectionsApi.newRequest(geoClient)
				.mode(TravelMode.DRIVING)
				.origin(origin)
				.destination(routeDest)
		directionsRequest.setCallback(object: PendingResult.Callback<DirectionsResult> {
			override fun onFailure(e: Throwable?) {
				Log.w(TAG, "Failed to find route!")
//				throw e ?: return
			}

			override fun onResult(result: DirectionsResult?) {
				if (result == null || result.routes.isEmpty()) { return }
				Log.i(TAG, "Adding route to map")
				// this needs to be on the main thread
				val decodedPath = result.routes[0].overviewPolyline.decodePath()
				val currentNavRoute = decodedPath.map {
					// convert from route LatLng to map LatLng
					LatLng(it.lat, it.lng)
				}
				this@GMapsController.currentNavRoute = currentNavRoute
				// the results come on a network thread, but we need to draw them in the UI thread
				handler.post {
					projection?.map?.addPolyline(PolylineOptions().color(context.getColor(R.color.mapRouteLine)).addAll(currentNavRoute))
				}
			}
		})

		// set up camera animations
		animatingCamera = true
		val navigationBounds = LatLngBounds.builder()
				.include(lastLocation)
				.include(destLatLng)
				.build()
		val currentVisibleRegion = projection?.map?.projection?.visibleRegion?.latLngBounds
		if (currentVisibleRegion == null || !currentVisibleRegion.contains(navigationBounds.northeast) || !currentVisibleRegion.contains(navigationBounds.southwest)) {
			handler.postDelayed({
				projection?.map?.animateCamera(CameraUpdateFactory.newLatLngBounds(navigationBounds, NAVIGATION_MAP_STARTZOOM_PADDING))
			}, 100)
		}

		handler.postDelayed({
			projection?.map?.animateCamera(CameraUpdateFactory.newLatLngZoom(lastLocation, currentZoom.toFloat()))
			animatingCamera = false
		}, NAVIGATION_MAP_STARTZOOM_TIME.toLong())
	}

	override fun stopNavigation() {
		// clear out previous nav
		projection?.map?.clear()
		currentNavDestination = null
		currentNavRoute = null
	}
}