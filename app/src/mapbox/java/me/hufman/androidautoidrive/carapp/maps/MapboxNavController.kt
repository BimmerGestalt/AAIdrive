package me.hufman.androidautoidrive.carapp.maps

import android.util.Log
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.core.constants.Constants.PRECISION_6
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import me.hufman.androidautoidrive.BuildConfig
import me.hufman.androidautoidrive.maps.CarLocationProvider
import me.hufman.androidautoidrive.maps.LatLong
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

fun LatLong.toPoint(): Point {
	return Point.fromLngLat(this.longitude, this.latitude)
}

class MapboxNavController(val client: MapboxDirections.Builder, val locationProvider: CarLocationProvider, val callback: (MapboxNavController) -> Unit) {
	companion object {
		fun getInstance(locationProvider: CarLocationProvider, callback: (MapboxNavController) -> Unit): MapboxNavController {
			val client = MapboxDirections.builder()
					.overview(DirectionsCriteria.OVERVIEW_FULL)
					.profile(DirectionsCriteria.PROFILE_DRIVING_TRAFFIC)
					.accessToken(BuildConfig.MapboxAccessToken)
			return MapboxNavController(client, locationProvider, callback)
		}
	}

	var currentNavDestination: LatLong? = null
		private set
	var currentNavRoute: LineString? = null
		private set

	fun navigateTo(dest: LatLong) {
		Log.i(TAG, "Starting navigation to $dest")
		currentNavDestination = dest

		val currentLocation = locationProvider.currentLocation
		if (currentLocation == null) {
			Log.w(TAG, "No car location yet, cancelling route search")
			return
		}
		routeNavigation(LatLong(currentLocation.latitude, currentLocation.longitude), dest, currentLocation.bearing)
	}

	fun stopNavigation() {
		Log.i(TAG, "Stopping navigation")
		currentNavDestination = null
		currentNavRoute = null
		callback(this)
	}

	private fun routeNavigation(start: LatLong, dest: LatLong, bearing: Float) {
		val request = client.waypoints(ArrayList())     // clear any previous origin/destination
				.bearings(ArrayList())
				.origin(start.toPoint())
				.destination(dest.toPoint())
//				.addBearing(bearing.toDouble(), 90.0)
				.build()
		request.enqueueCall(object: Callback<DirectionsResponse> {
			override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
				val data = response.body()
				Log.i(TAG, data.toString())
				val route = data?.routes()?.getOrNull(0)
				val geometry = route?.geometry()
				if (geometry == null) {
					Log.w(TAG, "Failed to find route! ${data?.message()}")
					return
				}
				currentNavRoute = LineString.fromPolyline(geometry, PRECISION_6)
				Log.i(TAG, "Decoded polyline $currentNavRoute")
				callback(this@MapboxNavController)
			}

			override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
				Log.w(TAG, "Failed to find route! $t")
			}
		})
	}
}