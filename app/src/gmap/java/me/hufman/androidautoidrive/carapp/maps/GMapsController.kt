package me.hufman.androidautoidrive.carapp.maps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import android.util.Log
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.*
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.PendingResult
import com.google.maps.model.DirectionsResult
import com.google.maps.model.TravelMode
import me.hufman.androidautoidrive.AppSettingsObserver
import me.hufman.androidautoidrive.R
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class GMapsController(private val context: Context, private val resultsController: MapResultsController, private val virtualDisplay: VirtualDisplay, val appSettings: AppSettingsObserver): MapInteractionController {
	val TAG = "GMapsController"
	var handler = Handler(context.mainLooper)
	var projection: GMapsProjection? = null

	private val SHUTDOWN_WAIT_INTERVAL = 120000L   // milliseconds of inactivity before shutting down map
	private val SEARCH_SESSION_TTL = 180000L    // number of milliseconds that a search session can live   https://stackoverflow.com/a/52339858

	private val placesClient: PlacesClient
	private val geoClient = GeoApiContext().setQueryRateLimit(3)
			.setApiKey(context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
					.metaData.getString("com.google.android.geo.API_KEY"))
			.setConnectTimeout(2, TimeUnit.SECONDS)
			.setReadTimeout(2, TimeUnit.SECONDS)
			.setWriteTimeout(2, TimeUnit.SECONDS)

	private var lastSettingsTime = 0L   // the last time we checked settings, for day/night check
	private val SETTINGS_TIME_INTERVAL = 5 * 60000  // milliseconds between checking day/night

	val locationProvider = LocationServices.getFusedLocationProviderClient(context)!!
	val locationCallback = LocationCallbackImpl()
	var currentLocation: LatLng? = null

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
	private var searchSessionStart: Long = 0    // when the search session started
	private var searchSessionConsumed = true    // if we used the search session for a Place Info lookup
	private var searchSession: AutocompleteSessionToken? = null // the search session token
	private var currentNavDestination: LatLong? = null
	var currentNavRoute: List<LatLng>? = null

	init {
		val api_key = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
				.metaData.getString("com.google.android.geo.API_KEY") ?: ""
		Places.initialize(context, api_key)
		placesClient = Places.createClient(context)
	}

	override fun showMap() {
		Log.i(TAG, "Beginning map projection")

		// cancel a shutdown timer
		handler.removeCallbacks(shutdownMapRunnable)

		if (projection == null) {
			Log.i(TAG, "First showing of the map")
			val projection = GMapsProjection(context, virtualDisplay.display, appSettings)
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
		if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			val locationRequest = LocationRequest()
			locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
			locationRequest.interval = 3000
			locationRequest.fastestInterval = 500

			locationProvider.requestLocationUpdates(locationRequest, locationCallback, null)
		}
	}

	override fun pauseMap() {
		locationProvider.removeLocationUpdates(locationCallback)

		handler.postDelayed(shutdownMapRunnable, SHUTDOWN_WAIT_INTERVAL)
	}

	private val shutdownMapRunnable = Runnable {
		Log.i(TAG, "Shutting down GMapProjection due to inactivity of ${SHUTDOWN_WAIT_INTERVAL}ms")
		projection?.hide()
		projection = null
	}

	inner class LocationCallbackImpl: LocationCallback() {
		override fun onLocationResult(location: LocationResult?) {
			if (location != null && location.lastLocation != null) {
				if (currentLocation == null) {  // first view
					initCamera()
				}

				// save the new location
				currentLocation = LatLng(location.lastLocation.latitude, location.lastLocation.longitude)
				projection?.location = currentLocation

				// move the camera to the new location
				updateCamera()

				// check to re-apply day/night settings after an interval
				if (lastSettingsTime + SETTINGS_TIME_INTERVAL < System.currentTimeMillis()) {
					projection?.applySettings()
					lastSettingsTime = System.currentTimeMillis()
				}
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

	private fun initCamera() {
		// set the camera to the starting position
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
			animatingCamera = true
			projection?.map?.animateCamera(CameraUpdateFactory.newLatLngZoom(location, currentZoom), animationFinishedCallback)
		}
	}

	override fun searchLocations(query: String) {
		val latlngBounds = projection?.map?.projection?.visibleRegion?.latLngBounds ?: return
		searchLocations(query, latlngBounds)
	}

	private fun generateSearchSession() {
		// the search session only should live for 3 minutes
		if (searchSessionStart + SEARCH_SESSION_TTL < System.currentTimeMillis()) {
			searchSession = null
		}
		if (searchSession == null) {
			searchSession = AutocompleteSessionToken.newInstance()
			searchSessionStart = System.currentTimeMillis()
			searchSessionConsumed = false
		}
	}
	fun searchLocations(query: String, location: LatLngBounds) {
		generateSearchSession()

		val bounds = RectangularBounds.newInstance(location)
		Log.i(TAG, "Starting Place search for $query near $bounds")
		val autocompleteRequest = FindAutocompletePredictionsRequest.builder()
				.setLocationBias(bounds)
				.setSessionToken(searchSession)
				.setQuery(query)
				.build()
		placesClient.findAutocompletePredictions(autocompleteRequest).addOnSuccessListener { result ->
			val results = result?.autocompletePredictions ?: return@addOnSuccessListener
			Log.i(TAG, "Received ${results.size} results for query $query")

			val mapResults = results.map {
				MapResult(it.placeId, it.getPrimaryText(null).toString(), it.getSecondaryText(null).toString())
			}
			resultsController.onSearchResults(mapResults.toTypedArray())
		}.addOnFailureListener {
			Log.w(TAG, "Unsuccessful result when loading results for $query: $it")
		}
	}

	override fun resultInformation(resultId: String) {
		val requestedFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
		val requestBuilder = FetchPlaceRequest.builder(resultId, requestedFields)
		if (!searchSessionConsumed) {
			requestBuilder.setSessionToken(searchSession)
			searchSessionConsumed = true
		}
		val request = requestBuilder.build()

		placesClient.fetchPlace(request).addOnSuccessListener { result ->
			Log.i(TAG, "Received Place result for resultId $resultId: ${result?.place}")
			val place = result?.place ?: return@addOnSuccessListener
			val latLng = place.latLng
			if (latLng == null) {
				Log.w(TAG, "Place does not have a latlng location!")
				return@addOnSuccessListener
			}
			val mapResult = MapResult(resultId, place.name.toString(), place.address.toString(),
					LatLong(latLng.latitude, latLng.longitude))
			resultsController.onPlaceResult(mapResult)
		}.addOnFailureListener {
			Log.w(TAG, "Did not find Place info for Place ID $resultId: $it")
		}
	}

	override fun navigateTo(dest: LatLong) {
		Log.i(TAG, "Beginning navigation to $dest")
		// clear out previous nav
		projection?.map?.clear()
		// show new nav destination icon
		currentNavDestination = dest
		drawNavigation()

		// search for a route
		routeNavigation()

		// start zoom animation
		animateNavigation()
	}

	private fun animateNavigation() {
		// show a camera animation to zoom out to the whole navigation route
		val dest = currentNavDestination ?: return
		val lastLocation = currentLocation ?: return
		animatingCamera = true
		// zoom out to the full view
		val destLatLng = LatLng(dest.latitude, dest.longitude)

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

		// then zoom back in to the user's chosen zoom
		handler.postDelayed({
			projection?.map?.animateCamera(CameraUpdateFactory.newLatLngZoom(lastLocation, currentZoom))
			animatingCamera = false
		}, NAVIGATION_MAP_STARTZOOM_TIME.toLong())
	}

	private fun routeNavigation() {
		// start a route search
		val lastLocation = currentLocation ?: return
		val dest = currentNavDestination ?: return
		val origin = com.google.maps.model.LatLng(lastLocation.latitude, lastLocation.longitude)
		val routeDest = com.google.maps.model.LatLng(dest.latitude, dest.longitude)

		val directionsRequest = DirectionsApi.newRequest(geoClient)
				.mode(TravelMode.DRIVING)
				.origin(origin)
				.destination(routeDest)
		directionsRequest.setCallback(object: PendingResult.Callback<DirectionsResult> {
			override fun onFailure(e: Throwable?) {
				Log.w(TAG, "Failed to find route! $e")
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
				drawNavigation()
			}
		})
	}

	private fun drawNavigation() {
		// make sure we are in the UI thread, and then draw navigation lines onto it
		// because route search comes back on a network thread
		val action = {
			projection?.map?.clear()

			// destination flag
			val dest = currentNavDestination
			if (dest != null) {
				val destLatLng = LatLng(dest.latitude, dest.longitude)
				val marker = MarkerOptions()
						.position(destLatLng)
						.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
						.visible(true)
				projection?.map?.addMarker(marker)
			}

			// routing
			val currentNavRoute = this.currentNavRoute
			if (currentNavRoute != null) {
				projection?.map?.addPolyline(PolylineOptions().color(context.getColor(R.color.mapRouteLine)).addAll(currentNavRoute))
			}
		}
		if (Looper.myLooper() != handler.looper) {
			handler.post(action)
		} else {
			action()
		}
	}

	override fun stopNavigation() {
		// clear out previous nav
		currentNavDestination = null
		currentNavRoute = null
		drawNavigation()
	}
}