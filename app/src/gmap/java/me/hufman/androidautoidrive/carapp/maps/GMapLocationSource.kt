package me.hufman.androidautoidrive.carapp.maps

import android.location.Location
import com.google.android.gms.maps.LocationSource

class GMapLocationSource: LocationSource {
	private var listener: LocationSource.OnLocationChangedListener? = null
	var location: Location? = null
		private set

	override fun activate(p0: LocationSource.OnLocationChangedListener) {
		listener = p0
	}

	override fun deactivate() {
		listener = null
	}

	fun onLocationUpdate(location: Location) {
		listener ?: return

		this.location = location
		listener?.onLocationChanged(location)
	}
}