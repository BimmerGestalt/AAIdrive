package me.hufman.androidautoidrive.maps

import android.content.Context
import me.hufman.androidautoidrive.CarInformation

class PlaceSearchProvider(private val context: Context) {
	fun getInstance(): MapPlaceSearch {
		return MapboxPlaceSearch.getInstance(CdsLocationProvider(CarInformation.cachedCdsData))
	}
}