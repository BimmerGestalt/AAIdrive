package me.hufman.androidautoidrive.maps

import android.content.Context
import me.hufman.androidautoidrive.CarInformation

class PlaceSearchProvider(private val context: Context) {
	fun getInstance(): MapPlaceSearch {
		val locationProvider = CdsLocationProvider(CarInformation.cachedCdsData, false)
		return GMapsPlaceSearch.getInstance(context, locationProvider)
	}
}