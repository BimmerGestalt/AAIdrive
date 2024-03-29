package me.hufman.androidautoidrive.maps

import kotlinx.coroutines.Deferred
import java.io.Serializable

data class MapResult(val id: String, val name: String,
                     val address: String? = null,
                     val location: LatLong? = null,
                     val distanceKm: Float? = null): Serializable {
	override fun toString(): String {
		return if (address != null) {
			if (name.isNotBlank()) {
				"$name\n$address"
			} else {
				address
			}
		} else {
			name
		}
	}
}

interface MapPlaceSearch {
	fun searchLocationsAsync(query: String): Deferred<List<MapResult>>
	fun resultInformationAsync(resultId: String): Deferred<MapResult?>
}