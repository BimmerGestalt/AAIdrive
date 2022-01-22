package me.hufman.androidautoidrive.maps

import android.content.Context
import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import java.io.IOException

fun Address.addressLines(): List<String> {
	return (0..this.maxAddressLineIndex).map { i ->
		this.getAddressLine(i)
	}
}
class AddressPlaceSearch(val geocoder: Geocoder): MapPlaceSearch {
	companion object {
		fun getInstance(context: Context): AddressPlaceSearch {
			return AddressPlaceSearch(Geocoder(context))
		}
	}

	override fun searchLocationsAsync(query: String): Deferred<List<MapResult>> {
		val results = CompletableDeferred<List<MapResult>>()
		try {
			val searchResults = geocoder.getFromLocationName(query, 5)
			results.complete(searchResults.map {
				val name = if (!it.getAddressLine(0).startsWith(it.featureName)) { it.featureName } else { "" }
				MapResult(it.url ?: "", name, it.addressLines().joinToString(", "),
				LatLong(it.latitude, it.longitude))
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