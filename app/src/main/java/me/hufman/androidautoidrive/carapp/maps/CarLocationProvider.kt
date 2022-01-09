package me.hufman.androidautoidrive.carapp.maps

import android.location.Location
import io.bimmergestalt.idriveconnectkit.CDS
import me.hufman.androidautoidrive.carapp.CDSData
import me.hufman.androidautoidrive.carapp.subscriptions
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsDouble
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonObject
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonPrimitive
import java.io.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

data class LatLong(val latitude: Double, val longitude: Double): Serializable {
	/**
	 * Returns the distance to the other point in KM
	 */
	fun distanceFrom(other: LatLong): Double {
		// from https://stackoverflow.com/a/1253545
		// hilariously inaccurate, but probably good enough
		val latDistance = abs(other.latitude - this.latitude) * 110.574
		val latRadians = this.latitude * PI / 180
		val longDistance = abs(other.longitude - this.longitude) * cos(latRadians)
		return sqrt(latDistance*latDistance + longDistance*longDistance)
	}
}
data class CarHeading(val heading: Float, val speed: Float): Serializable

class CarLocationProvider(val cdsData: CDSData) {
	var currentLatLong: LatLong? = null
		private set
	var currentHeading: CarHeading? = null
		private set
	val currentLocation: Location?
		get() {
			val latLong = currentLatLong
			val heading = currentHeading
			return if (latLong != null) {
				Location("CarLocationProvider").also {
					it.latitude = latLong.latitude
					it.longitude = latLong.longitude
					if (heading != null) {
						it.bearing = heading.heading
						it.speed = heading.speed
					}
				}
			} else {
				null
			}
		}

	var callback: ((Location) -> Unit)? = null

	init {
		parseGPS()
		parseHeading()
	}

	fun start() {
		cdsData.subscriptions[CDS.NAVIGATION.GPSPOSITION] = {
			parseGPS()
		}
		cdsData.subscriptions[CDS.NAVIGATION.GPSEXTENDEDINFO] = {
			parseHeading()
		}
	}

	private fun parseGPS() {
		val gpsPosition = cdsData[CDS.NAVIGATION.GPSPOSITION] ?: return
		val position = gpsPosition.tryAsJsonObject("GPSPosition")
		val latitude = position?.tryAsJsonPrimitive("latitude")?.tryAsDouble
		val longitude = position?.tryAsJsonPrimitive("longitude")?.tryAsDouble
		if (longitude != null && latitude != null) {
			currentLatLong = LatLong(latitude, longitude)
			currentLocation?.also { location -> callback?.invoke(location) }
		}
	}

	private fun parseHeading() {
		val gpsHeading = cdsData[CDS.NAVIGATION.GPSEXTENDEDINFO] ?: return
		val position = gpsHeading.tryAsJsonObject("GPSExtendedInfo")
		val heading = position?.tryAsJsonPrimitive("heading")?.tryAsDouble   // in degrees, needs to be negated for Location usage
		val speed = position?.tryAsJsonPrimitive("speed")?.tryAsDouble ?: 0.0  // in kmph
		val validSpeed = if (speed < 4000) speed else 0
		if (heading != null) {
			currentHeading = CarHeading(-heading.toFloat(), validSpeed.toFloat() / 3.6f)
			currentLocation?.also { location -> callback?.invoke(location) }
		}
	}

	fun stop() {
		cdsData.subscriptions[CDS.NAVIGATION.GPSPOSITION] = null
		cdsData.subscriptions[CDS.NAVIGATION.GPSEXTENDEDINFO] = null
	}
}