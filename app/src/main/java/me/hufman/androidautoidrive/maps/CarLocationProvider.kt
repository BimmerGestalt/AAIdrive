package me.hufman.androidautoidrive.maps

import android.location.Location
import com.google.gson.JsonObject
import io.bimmergestalt.idriveconnectkit.CDS
import io.bimmergestalt.idriveconnectkit.CDSProperty
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.carapp.CDSData
import me.hufman.androidautoidrive.carapp.CDSEventHandler
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
		val longDistance = abs(other.longitude - this.longitude) * 111.320 * cos(latRadians)
		return sqrt(latDistance*latDistance + longDistance*longDistance)
	}
}
data class CarHeading(val heading: Float, val speed: Float): Serializable

abstract class CarLocationProvider {
	var currentLocation: Location? = null
		protected set

	var callback: ((Location) -> Unit)? = null

	protected fun sendCallback() {
		currentLocation?.also { location -> callback?.invoke(location) }
	}

	abstract fun start()
	abstract fun stop()
}

class CdsLocationProvider(val cdsData: CDSData): CarLocationProvider() {
	var currentLatLong: LatLong? = null
	var currentHeading: CarHeading? = null

	init {
		parseGPS()
		parseHeading()
		cdsData.addEventHandler(CDS.NAVIGATION.GPSPOSITION, 10000, object: CDSEventHandler {
			override fun onPropertyChangedEvent(property: CDSProperty, propertyValue: JsonObject) {
				parseGPS()
			}
		})
	}

	override fun start() {
		cdsData.subscriptions[CDS.NAVIGATION.GPSPOSITION] = {
			parseGPS()
		}
		cdsData.subscriptions[CDS.NAVIGATION.GPSEXTENDEDINFO] = {
			parseHeading()
		}
		sendCallback()
	}

	private fun parseGPS() {
		val gpsPosition = cdsData[CDS.NAVIGATION.GPSPOSITION] ?: return
		val position = gpsPosition.tryAsJsonObject("GPSPosition")
		val latitude = position?.tryAsJsonPrimitive("latitude")?.tryAsDouble
		val longitude = position?.tryAsJsonPrimitive("longitude")?.tryAsDouble
		if (longitude != null && latitude != null) {
			currentLatLong = LatLong(latitude, longitude)
			onLocationUpdate()
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
			onLocationUpdate()
		}
	}

	private fun onLocationUpdate() {
		val latLong = currentLatLong
		val heading = currentHeading
		currentLocation = if (latLong != null) {
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
		sendCallback()
	}

	override fun stop() {
		cdsData.subscriptions[CDS.NAVIGATION.GPSPOSITION] = null
		cdsData.subscriptions[CDS.NAVIGATION.GPSEXTENDEDINFO] = null
	}
}

class CombinedLocationProvider(val appSettings: AppSettings,
                               val phoneLocationProvider: CarLocationProvider,
                               val carLocationProvider: CarLocationProvider): CarLocationProvider() {
	private val preferPhoneLocation: Boolean
		get() = appSettings[AppSettings.KEYS.MAP_USE_PHONE_GPS].toBoolean()

	init {
		phoneLocationProvider.callback = {
			currentLocation = it
			sendCallback()
		}
		carLocationProvider.callback = {
			currentLocation = it
			sendCallback()
		}
	}

	override fun start() {
		if (preferPhoneLocation) {
			phoneLocationProvider.start()
		} else {
			carLocationProvider.start()
		}
	}

	override fun stop() {
		phoneLocationProvider.stop()
		carLocationProvider.stop()
	}
}