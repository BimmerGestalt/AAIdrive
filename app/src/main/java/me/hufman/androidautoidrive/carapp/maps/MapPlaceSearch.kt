package me.hufman.androidautoidrive.carapp.maps

import kotlinx.coroutines.Deferred
import java.io.Serializable

data class MapResult(val id: String, val name: String,
                     val address: String? = null,
                     val location: LatLong? = null): Serializable {
	override fun toString(): String {
		if (address != null) {
			return "$name\n$address"
		}
		return name
	}
}

interface MapPlaceSearch {
	fun searchLocationsAsync(query: String): Deferred<List<MapResult>>
	fun resultInformationAsync(resultId: String): Deferred<MapResult?>
}