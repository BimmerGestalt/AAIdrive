package me.hufman.androidautoidrive.maps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*

class AndroidLocationProvider(val locationProvider: FusedLocationProviderClient): CarLocationProvider() {
	companion object {
		fun getInstance(context: Context): AndroidLocationProvider {
			return AndroidLocationProvider(LocationServices.getFusedLocationProviderClient(context))
		}
	}
	private val locationCallback = LocationCallbackImpl()

	inner class LocationCallbackImpl: LocationCallback() {
		override fun onLocationResult(result: LocationResult) {
			onLocationUpdate(result.lastLocation)
		}
	}

	@SuppressLint("MissingPermission")
	override fun start() {
		val locationRequest = LocationRequest.create()
		locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
		locationRequest.interval = 3000
		locationRequest.fastestInterval = 500

		locationProvider.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
	}

	private fun onLocationUpdate(location: Location) {
		currentLocation = location
		sendCallback()
	}

	override fun stop() {
		locationProvider.removeLocationUpdates(locationCallback)
	}
}