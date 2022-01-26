package me.hufman.androidautoidrive.maps

import android.content.Context
import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import me.hufman.androidautoidrive.CarInformation
import java.io.IOException

fun Address.addressLines(): List<String> {
	return (0..this.maxAddressLineIndex).map { i ->
		this.getAddressLine(i)
	}
}
class AddressPlaceSearch(val geocoder: Geocoder, private val locationProvider: CarLocationProvider): MapPlaceSearch {
	companion object {
		fun getInstance(context: Context): AddressPlaceSearch {
			return AddressPlaceSearch(Geocoder(context), CdsLocationProvider(CarInformation.cachedCdsData))
		}
	}

	override fun searchLocationsAsync(query: String): Deferred<List<MapResult>> {
		val results = CompletableDeferred<List<MapResult>>()
		try {
			val currentLocation = locationProvider.currentLocation?.let {
				LatLong(it.latitude, it.longitude)
			}
			val searchResults = geocoder.getFromLocationName(query, 5)
			results.complete(searchResults.map {
				val name = if (!it.getAddressLine(0).startsWith(it.featureName)) { it.featureName } else { "" }
				val placeLocation = LatLong(it.latitude, it.longitude)
				MapResult(it.url ?: "", name, it.addressLines().joinToString(", "),
				placeLocation, currentLocation?.distanceFrom(placeLocation)?.toFloat())
			})
		} catch (e: IOException) {
			results.complete(emptyList())
		}
		return results
	}

	override fun resultInformationAsync(resultId: String): Deferred<MapResult?> {
		return CompletableDeferred(null)
	}
}