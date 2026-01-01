package me.hufman.androidautoidrive.phoneui.controllers

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.bimmergestalt.idriveconnectkit.CDS
import io.bimmergestalt.idriveconnectkit.CDSProperty
import me.hufman.androidautoidrive.MapTestingService
import me.hufman.androidautoidrive.carapp.maps.MapInteractionController
import me.hufman.androidautoidrive.cds.CDSDataProvider

class MapTestingController(
	val mapInteractionController: MapInteractionController,
	val cdsData: CDSDataProvider
) {
	fun zoomIn(steps: Int = 1) = mapInteractionController.zoomIn(steps)
	fun zoomOut(steps: Int = 1) = mapInteractionController.zoomOut(steps)

	fun moveForward() {
		val currentLocation = cdsData[CDS.NAVIGATION.GPSPOSITION] ?: return
		val currentLatitude = currentLocation.getAsJsonObject("GPSPosition")
			?.getAsJsonPrimitive("latitude")
			?.asDouble ?: return
		val currentLongitude = currentLocation.getAsJsonObject("GPSPosition")
			?.getAsJsonPrimitive("longitude")
			?.asDouble ?: return
		val currentExtendedInfo = cdsData[CDS.NAVIGATION.GPSEXTENDEDINFO]
		val heading = currentExtendedInfo?.getAsJsonObject("GPSExtendedInfo")
			?.getAsJsonPrimitive("heading")
			?.asDouble
			?: 0.0

		// https://stackoverflow.com/a/47572447
		val headingRads = -Math.toRadians(heading - 90)
		val distance = 100.0
		val newLatitude = currentLatitude + (180/Math.PI) * (distance / 6378137) * Math.sin(headingRads)
		val newLongitude = currentLongitude + (180/Math.PI) * (distance / 6378137)/Math.cos(currentLatitude) * Math.cos(headingRads)

		Log.i(MapTestingService.TAG, "Moving from ${currentLatitude}x${currentLongitude} to ${newLatitude}x${newLongitude}")
		cdsData.onPropertyChangedEvent(CDSProperty.NAVIGATION_GPSPOSITION, JsonObject().apply {
			add("GPSPosition", JsonObject().apply {
				add("latitude", JsonPrimitive(newLatitude))
				add("longitude",  JsonPrimitive(newLongitude))
			})
		})
	}

	fun steer(angle: Double) {
		val currentExtendedInfo = cdsData[CDS.NAVIGATION.GPSEXTENDEDINFO]
		val heading = currentExtendedInfo?.getAsJsonObject("GPSExtendedInfo")
			?.getAsJsonPrimitive("heading")
			?.asDouble
			?: 0.0

		val newHeading = heading + angle
		val newHeadingWrapped = if (newHeading < 0) {
			newHeading + 360
		} else if (newHeading > 360) {
			newHeading - 360
		} else {
			newHeading
		}

		cdsData.onPropertyChangedEvent(CDSProperty.NAVIGATION_GPSEXTENDEDINFO, JsonObject().apply {
			add("GPSExtendedInfo", JsonObject().apply {
				add("heading", JsonPrimitive(newHeadingWrapped))
			})
		})
	}

	fun steerLeft() = steer(30.0)
	fun steerRight() = steer(-30.0)
}