package me.hufman.androidautoidrive.maps

import android.location.Location
import com.mapbox.api.geocoding.v5.MapboxGeocoding
import com.mapbox.api.geocoding.v5.models.CarmenFeature
import com.mapbox.api.geocoding.v5.models.GeocodingResponse
import com.mapbox.geojson.Point
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import me.hufman.androidautoidrive.BuildConfig
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonPrimitive
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsString
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

fun Point.toLatLong(): LatLong {
	return LatLong(this.latitude(), this.longitude())
}
fun Point(location: Location?): Point? {
	return location?.let {
		Point.fromLngLat(it.longitude, it.latitude)
	}
}
fun MapResult(feature: CarmenFeature, origin: LatLong?): MapResult {
	val featureLocation = feature.center()?.toLatLong()
	val distance = if (origin != null && featureLocation != null) {
		featureLocation.distanceFrom(origin)
	} else { null }

	val propertyAddress = feature.properties()?.tryAsJsonPrimitive("address")?.tryAsString
	val featureName = feature.matchingPlaceName() ?: feature.placeName() ?: ""
	return if (feature.placeType()?.contains("address") == true) {
		MapResult(feature.id() ?: "", "", featureName,
				featureLocation, distance?.toFloat())
	} else if (propertyAddress != null && featureName.contains(", $propertyAddress")) {
		val pieces = featureName.split(", $propertyAddress")
		val name = pieces[0]
		val address = propertyAddress + pieces[1]
		MapResult(feature.id() ?: "", name, address,
				featureLocation, distance?.toFloat())
	} else {
		MapResult(feature.id() ?: "", featureName, feature.address() ?: propertyAddress,
				featureLocation, distance?.toFloat())
	}
}

class MapboxPlaceSearch(val searchEngine: MapboxGeocoding.Builder, val locationProvider: CarLocationProvider): MapPlaceSearch {
	companion object {
		fun getInstance(locationProvider: CarLocationProvider): MapboxPlaceSearch {
			val client = MapboxGeocoding.builder()
					.accessToken(BuildConfig.MapboxAccessToken)
					.clientAppName(BuildConfig.APPLICATION_ID)

			return MapboxPlaceSearch(client, locationProvider)
		}
	}

	override fun searchLocationsAsync(query: String): Deferred<List<MapResult>> {
		if (query.length < 3) {
			return CompletableDeferred(emptyList())
		}
		val results = CompletableDeferred<List<MapResult>>()
		val location = locationProvider.currentLocation
		val latLong = location?.let { LatLong(it.latitude, it.longitude) }
		val point = Point(location)
		if (point != null) {
			searchEngine.proximity(point)
		}

		val search = searchEngine.query(query).build()
		search.enqueueCall(object: Callback<GeocodingResponse> {
			override fun onResponse(call: Call<GeocodingResponse>, response: Response<GeocodingResponse>) {
				val resultPlaces = response.body()?.features()?.map {
//					println(it)
					MapResult(it, latLong)
				} ?: emptyList()
				results.complete(resultPlaces)
			}

			override fun onFailure(call: Call<GeocodingResponse>, t: Throwable) {
				results.complete(emptyList())
			}
		})

		return results
	}

	override fun resultInformationAsync(resultId: String): Deferred<MapResult?> {
		return CompletableDeferred(null as MapResult?)
	}
}