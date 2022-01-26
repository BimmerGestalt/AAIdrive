package me.hufman.androidautoidrive.maps

import android.content.Context

class PlaceSearchProvider(private val context: Context) {
	fun getInstance(): MapPlaceSearch {
		return AddressPlaceSearch.getInstance(context)
	}
}