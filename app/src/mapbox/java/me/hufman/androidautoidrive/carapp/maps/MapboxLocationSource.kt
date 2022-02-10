package me.hufman.androidautoidrive.carapp.maps

import android.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.locationcomponent.LocationConsumer
import com.mapbox.maps.plugin.locationcomponent.LocationProvider

class MapboxLocationSource: LocationProvider {
	private var listener: LocationConsumer? = null
	var location: Location? = null
		private set

	override fun registerLocationConsumer(locationConsumer: LocationConsumer) {
		listener = locationConsumer
		location?.also { onLocationUpdate(it) }
	}

	override fun unRegisterLocationConsumer(locationConsumer: LocationConsumer) {
		listener = null
	}

	fun onLocationUpdate(location: Location) {
		listener ?: return

		this.location = location
		listener?.onLocationUpdated(Point.fromLngLat(location.longitude, location.latitude))
		listener?.onBearingUpdated(location.bearing.toDouble())
	}
}