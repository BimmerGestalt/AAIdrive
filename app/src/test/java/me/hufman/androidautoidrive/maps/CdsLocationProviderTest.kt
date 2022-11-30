package me.hufman.androidautoidrive.maps

import com.google.gson.JsonObject
import io.bimmergestalt.idriveconnectkit.CDS
import me.hufman.androidautoidrive.carapp.CDSDataProvider
import me.hufman.androidautoidrive.carapp.subscriptions
import org.junit.Assert.*
import org.junit.Test

class CdsLocationProviderTest {
	val cdsData = CDSDataProvider()

	val gpsPosition = JsonObject().apply {
		add("GPSPosition", JsonObject().apply {
			addProperty("latitude", 12.345678)
			addProperty("longitude", -12.345678)
		})
	}
	val gpsHeading = JsonObject().apply {
		add("GPSExtendedInfo", JsonObject().apply {
			addProperty("altitude", 65530)
			addProperty("heading", 144)
			addProperty("quality", 443)
			addProperty("speed", 32768)
		})
	}

	@Test
	fun testParseEmpty() {
		val provider = CdsLocationProvider(cdsData)
		assertNull(provider.currentLatLong)
		assertNull(provider.currentHeading)
		assertNull(provider.currentLocation)
	}

	@Test
	fun testParseNan() {
		listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).forEach { value ->
			listOf("latitude", "longitude").forEach { key ->
				val position = JsonObject().apply {
					add("GPSPosition", JsonObject().apply {
						if (key == "latitude") {
							addProperty("latitude", value)
						} else {
							addProperty("latitude", 12.345678)
						}
						if (key == "longitude") {
							addProperty("longitude", value)
						} else {
							addProperty("longitude", -12.345678)
						}

					})
				}

				cdsData.onPropertyChangedEvent(CDS.NAVIGATION.GPSPOSITION, position)

				val provider = CdsLocationProvider(cdsData)
				assertNull(provider.currentLatLong)
				assertNull(provider.currentHeading)
				assertNull(provider.currentLocation)
			}
		}
	}

	@Test
	fun testParse() {
		cdsData.onPropertyChangedEvent(CDS.NAVIGATION.GPSPOSITION, gpsPosition)
		cdsData.onPropertyChangedEvent(CDS.NAVIGATION.GPSEXTENDEDINFO, gpsHeading)

		val provider = CdsLocationProvider(cdsData)
		assertNotNull(provider.currentLatLong)
		assertNotNull(provider.currentHeading)
		assertNotNull(provider.currentLocation)

		assertEquals(12.345678, provider.currentLatLong?.latitude)
		assertEquals(-12.345678, provider.currentLatLong?.longitude)
		assertEquals(-144f, provider.currentHeading?.heading)
		assertEquals(0f, provider.currentHeading?.speed)

		assertEquals(12.345678, provider.currentLocation?.latitude)
		assertEquals(-12.345678, provider.currentLocation?.longitude)
		assertEquals(-144f, provider.currentLocation?.bearing)
		assertEquals(0f, provider.currentLocation?.speed)
	}

	@Test
	fun testStart() {
		val provider = CdsLocationProvider(cdsData)
		assertNull(provider.currentLatLong)
		assertNull(provider.currentHeading)
		assertNull(provider.currentLocation)

		provider.start()
		assertNotNull(cdsData.subscriptions[CDS.NAVIGATION.GPSPOSITION])
		assertNotNull(cdsData.subscriptions[CDS.NAVIGATION.GPSEXTENDEDINFO])

		cdsData.onPropertyChangedEvent(CDS.NAVIGATION.GPSPOSITION, gpsPosition)
		cdsData.onPropertyChangedEvent(CDS.NAVIGATION.GPSEXTENDEDINFO, gpsHeading)

		assertEquals(12.345678, provider.currentLatLong?.latitude)
		assertEquals(-12.345678, provider.currentLatLong?.longitude)
		assertEquals(-144f, provider.currentHeading?.heading)
		assertEquals(0f, provider.currentHeading?.speed)

		assertEquals(12.345678, provider.currentLocation?.latitude)
		assertEquals(-12.345678, provider.currentLocation?.longitude)
		assertEquals(-144f, provider.currentLocation?.bearing)
		assertEquals(0f, provider.currentLocation?.speed)

		// test if location updates still come through
		provider.stop()
		assertNull(cdsData.subscriptions[CDS.NAVIGATION.GPSPOSITION])
		assertNull(cdsData.subscriptions[CDS.NAVIGATION.GPSEXTENDEDINFO])

		val gpsPositionNew = JsonObject().apply {
			add("GPSPosition", JsonObject().apply {
				addProperty("latitude", 32.345678)
				addProperty("longitude", -32.345678)
			})
		}
		cdsData.onPropertyChangedEvent(CDS.NAVIGATION.GPSPOSITION, gpsPositionNew)
		assertEquals(32.345678, provider.currentLatLong?.latitude)
		assertEquals(-32.345678, provider.currentLatLong?.longitude)
		assertEquals(32.345678, provider.currentLocation?.latitude)
		assertEquals(-32.345678, provider.currentLocation?.longitude)
	}
}