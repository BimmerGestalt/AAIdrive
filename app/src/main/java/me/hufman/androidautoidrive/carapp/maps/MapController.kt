package me.hufman.androidautoidrive.carapp.maps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.GET_META_DATA
import android.os.Bundle
import android.os.Handler
import android.util.Log
import com.google.android.gms.location.places.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.Task
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.PendingResult
import com.google.maps.model.DirectionsResult
import com.google.maps.model.TravelMode
import java.util.concurrent.TimeUnit
import me.hufman.androidautoidrive.R
import kotlin.math.max
import kotlin.math.min

const val INTENT_INTERACTION = "me.hufman.androidautoidrive.maps.INTERACTION"
const val EXTRA_INTERACTION_TYPE = "me.hufman.androidautoidrive.maps.INTERACTION.ZOOM_AMOUNT"
const val INTERACTION_SHOW_MAP = "me.hufman.androidautoidrive.maps.INTERACTION.SHOW_MAP"
const val INTERACTION_PAUSE_MAP = "me.hufman.androidautoidrive.maps.INTERACTION.PAUSE_MAP"
const val INTERACTION_ZOOM_IN = "me.hufman.androidautoidrive.maps.INTERACTION.ZOOM_IN"
const val INTERACTION_ZOOM_OUT = "me.hufman.androidautoidrive.maps.INTERACTION.ZOOM_OUT"
const val INTERACTION_SEARCH = "me.hufman.androidautoidrive.maps.INTERACTION.SEARCH"
const val INTERACTION_SEARCH_DETAILS = "me.hufman.androidautoidrive.maps.INTERACTION.SEARCH_DETAILS"
const val INTERACTION_NAV_START = "me.hufman.androidautoidrive.maps.INTERACTION.NAV_START"
const val INTERACTION_NAV_STOP = "me.hufman.androidautoidrive.maps.INTERACTION.NAV_STOP"
const val EXTRA_ZOOM_AMOUNT = "me.hufman.androidautoidrive.maps.INTERACTION.ZOOM_AMOUNT"
const val EXTRA_QUERY = "me.hufman.androidautoidrive.maps.INTERACTION.QUERY"
const val EXTRA_ID = "me.hufman.androidautoidrive.maps.INTERACTION.ID"
const val EXTRA_LATLONG = "me.hufman.androidautoidrive.maps.INTERACTION.LATLONG"

interface MapInteractionController {
	fun showMap()
	fun pauseMap()
	fun zoomIn(steps: Int = 1)
	fun zoomOut(steps: Int = 1)
	fun searchLocations(query: String)
	fun resultInformation(resultId: String)
	fun navigateTo(dest: LatLong)
	fun stopNavigation()
}

class MapInteractionControllerIntent(val context: Context): MapInteractionController {
	/** Used by the Car App to send interactions to the map in a different thread */
	private fun send(type: String, extras: Bundle = Bundle()) {
		val intent = Intent(INTENT_INTERACTION)
		intent.`package` = context.packageName
		intent.putExtras(extras)
		intent.putExtra(EXTRA_INTERACTION_TYPE, type)
		context.sendBroadcast(intent)
	}

	override fun showMap() {
		send(INTERACTION_SHOW_MAP)
	}

	override fun pauseMap() {
		send(INTERACTION_PAUSE_MAP)
	}

	override fun zoomIn(steps: Int) {
		send(INTERACTION_ZOOM_IN, Bundle().apply { putInt(EXTRA_ZOOM_AMOUNT, steps) })
	}

	override fun zoomOut(steps: Int) {
		send(INTERACTION_ZOOM_OUT, Bundle().apply { putInt(EXTRA_ZOOM_AMOUNT, steps) })
	}

	override fun searchLocations(query: String) {
		send(INTERACTION_SEARCH, Bundle().apply { putString(EXTRA_QUERY, query) })
	}

	override fun resultInformation(resultId: String) {
		send(INTERACTION_SEARCH_DETAILS, Bundle().apply { putString(EXTRA_ID, resultId) })
	}

	override fun navigateTo(dest: LatLong) {
		send(INTERACTION_NAV_START, Bundle().apply { putSerializable(EXTRA_LATLONG, dest) })
	}

	override fun stopNavigation() {
		send(INTERACTION_NAV_STOP)
	}
}


class MapsInteractionControllerListener(val context: Context, val controller: MapInteractionController) {
	val TAG = "MapControllerListener"

	/** Registers for interaction intents and routes requests to the controller methods */
	private val interactionListener = InteractionListener()

	fun onCreate() {
		context.registerReceiver(interactionListener, IntentFilter(INTENT_INTERACTION))
	}

	inner class InteractionListener: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			if (context == null || intent == null || context.packageName != intent.`package`) return
			Log.i(TAG, "Received interaction: ${intent.action}/${intent.getStringExtra(EXTRA_INTERACTION_TYPE)}")
			if (intent.action != INTENT_INTERACTION) return

			when (intent.getStringExtra(EXTRA_INTERACTION_TYPE)) {
				INTERACTION_SHOW_MAP -> controller.showMap()
				INTERACTION_PAUSE_MAP -> controller.pauseMap()
				INTERACTION_ZOOM_IN -> controller.zoomIn(intent.getIntExtra(EXTRA_ZOOM_AMOUNT, 1))
				INTERACTION_ZOOM_OUT -> controller.zoomOut(intent.getIntExtra(EXTRA_ZOOM_AMOUNT, 1))
				INTERACTION_SEARCH -> controller.searchLocations(intent.getStringExtra(EXTRA_QUERY) ?: "")
				INTERACTION_SEARCH_DETAILS -> controller.resultInformation(intent.getStringExtra(EXTRA_ID) ?: "")
				INTERACTION_NAV_START -> controller.navigateTo(intent.getSerializableExtra(EXTRA_LATLONG) as? LatLong ?: return)
				INTERACTION_NAV_STOP -> controller.stopNavigation()
				else -> Log.i(TAG, "Unknown interaction ${intent.getStringExtra(EXTRA_INTERACTION_TYPE)}")
			}
		}
	}

	open fun onDestroy() {
		context.unregisterReceiver(interactionListener)
		controller.pauseMap()
	}
}

class GMapsController(private val context: Context, private val resultsController: MapResultsController, private val screenCapture: VirtualDisplayScreenCapture): MapInteractionController {
	val TAG = "GMapsController"
	var projection: GMapsProjection? = null

	private val placesClient = Places.getGeoDataClient(context)!!
	private val geoClient = GeoApiContext().setQueryRateLimit(3)
			.setApiKey(context.packageManager.getApplicationInfo(context.packageName, GET_META_DATA)
					.metaData.getString("com.google.android.geo.API_KEY"))
			.setConnectTimeout(2, TimeUnit.SECONDS)
			.setReadTimeout(2, TimeUnit.SECONDS)
			.setWriteTimeout(2, TimeUnit.SECONDS)

	var currentZoom = 15
	var currentSearchResults: Task<AutocompletePredictionBufferResponse>? = null
	var currentNavDestination: LatLong? = null
	var currentNavRoute: List<LatLng>? = null

	override fun showMap() {
		Log.i(TAG, "Beginning map projection")
		if (projection == null) {
			Log.i(TAG, "First showing of the map")
			projection = GMapsProjection(context, screenCapture.virtualDisplay.display)
		}
		if (projection?.isShowing == false) {
			projection?.show()
		}
		// nudge the camera to trigger a redraw
		projection?.map?.animateCamera(CameraUpdateFactory.scrollBy(1f, 1f))
	}

	override fun pauseMap() {
//		projection?.hide()
	}

	override fun zoomIn(steps: Int) {
		Log.i(TAG, "Zooming map in $steps steps")
		currentZoom = min(20, currentZoom + steps)
		projection?.map?.animateCamera(CameraUpdateFactory.zoomTo(currentZoom.toFloat()))
	}
	override fun zoomOut(steps: Int) {
		Log.i(TAG, "Zooming map out $steps steps")
		currentZoom = max(0, currentZoom - steps)
		projection?.map?.animateCamera(CameraUpdateFactory.zoomTo(currentZoom.toFloat()))
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
		val lastLocation = projection?.lastLocation ?: return
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
				Handler(context.mainLooper).post {
					projection?.map?.addPolyline(PolylineOptions().color(context.getColor(R.color.mapRouteLine)).addAll(currentNavRoute))
				}
			}
		})
	}

	override fun stopNavigation() {
		// clear out previous nav
		projection?.map?.clear()
		currentNavDestination = null
		currentNavRoute = null
	}
}