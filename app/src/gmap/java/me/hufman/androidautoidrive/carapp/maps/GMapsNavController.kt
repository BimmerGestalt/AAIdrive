package me.hufman.androidautoidrive.carapp.maps

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.PendingResult
import com.google.maps.model.DirectionsResult
import com.google.maps.model.TravelMode
import me.hufman.androidautoidrive.maps.CarLocationProvider
import me.hufman.androidautoidrive.maps.LatLong
import java.util.concurrent.TimeUnit

class GMapsNavController(val geoClient: GeoApiContext, val locationProvider: CarLocationProvider, var callback: (GMapsNavController) -> Unit) {
	companion object {
		fun getInstance(context: Context, locationProvider: CarLocationProvider, callback: (GMapsNavController) -> Unit): GMapsNavController {
			val api_key = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
					.metaData.getString("com.google.android.geo.API_KEY") ?: ""
			val geoClient = GeoApiContext().setQueryRateLimit(3)
					.setApiKey(api_key)
					.setConnectTimeout(5, TimeUnit.SECONDS)
					.setReadTimeout(5, TimeUnit.SECONDS)
					.setWriteTimeout(5, TimeUnit.SECONDS)
			return GMapsNavController(geoClient, locationProvider, callback)
		}
	}

	var currentNavDestination: LatLong? = null
		private set
	var currentNavRoute: List<LatLng>? = null
		private set

	fun navigateTo(dest: LatLong) {
		currentNavDestination = dest

		val currentLocation = locationProvider.currentLocation ?: return
		routeNavigation(LatLong(currentLocation.latitude, currentLocation.longitude), dest)
	}

	fun stopNavigation() {
		currentNavDestination = null
		currentNavRoute = null
		callback(this)
	}

	private fun routeNavigation(start: LatLong, dest: LatLong) {
		// start a route search
		val origin = com.google.maps.model.LatLng(start.latitude, start.longitude)
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
				currentNavRoute = decodedPath.map {
					// convert from route LatLng to map LatLng
					LatLng(it.lat, it.lng)
				}
				callback(this@GMapsNavController)
			}
		})
	}
}