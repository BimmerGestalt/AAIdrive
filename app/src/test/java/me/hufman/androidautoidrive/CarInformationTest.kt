package me.hufman.androidautoidrive

import com.google.gson.JsonObject
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import me.hufman.androidautoidrive.carapp.CDSConnection
import me.hufman.idriveconnectionkit.CDSProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CarInformationTest {
	@Before
	fun setUp() {
		CarInformation.currentCapabilities = emptyMap()
		CarInformation.cachedCapabilities = emptyMap()
		CarInformation.cdsData.clear()
	}

	@Test
	fun testUpdate() {
		val carInformation = CarInformationUpdater(MockAppSettings())

		// onCapabilities
		val carCapabilities = mapOf("test" to "testvalue", "tts" to "true")
		carInformation.onCapabilities(carCapabilities)
		assertEquals(CarInformation.currentCapabilities, carInformation.capabilities)
		assertEquals(mapOf("tts" to "true"), CarInformation.cachedCapabilities)

		// onCdsConnection
		val cdsConnection = mock<CDSConnection>()
		carInformation.onCdsConnection(cdsConnection)
		carInformation.cdsData.addEventHandler(CDSProperty.NAVIGATION_GUIDANCESTATUS, 1000, mock())
		verify(cdsConnection).subscribeProperty(CDSProperty.NAVIGATION_GUIDANCESTATUS, 1000)

		// onPropertyChangedEvent
		val cdsValue = JsonObject().apply { addProperty("guidanceStatus", 1) }
		carInformation.onPropertyChangedEvent(CDSProperty.NAVIGATION_GUIDANCESTATUS, cdsValue)
		assertEquals(cdsValue, CarInformation.cdsData[CDSProperty.NAVIGATION_GUIDANCESTATUS])
	}

	@Test
	fun testCachedCapabilitiesLayer() {
		val carInformation = CarInformationUpdater(MockAppSettings())
		val carCapabilities = mapOf("test" to "testvalue", "tts" to "true")
		carInformation.capabilities = carCapabilities
		assertEquals(carCapabilities, CarInformation.currentCapabilities)
		assertEquals(mapOf("tts" to "true"), CarInformation.cachedCapabilities)
		assertEquals(CarInformation.currentCapabilities, carInformation.capabilities)

		CarInformation.currentCapabilities = emptyMap()
		assertEquals(CarInformation.cachedCapabilities, carInformation.capabilities)
	}

	@Test
	fun testCachedCapabilitiesPersistence() {
		// test empty cache
		val appSettings = MockAppSettings()
		CarInformation.loadCache(appSettings)
		assertTrue(CarInformation.currentCapabilities.isEmpty())
		assertTrue(CarInformation.cachedCapabilities.isEmpty())

		val carInformation = CarInformationUpdater(appSettings)
		val carCapabilities = mapOf("test" to "testvalue", "tts" to "true")
		carInformation.capabilities = carCapabilities
		assertEquals(mapOf("tts" to "true"), CarInformation.cachedCapabilities)
		CarInformation.saveCache(appSettings)

		CarInformation.currentCapabilities = emptyMap()
		CarInformation.cachedCapabilities = emptyMap()
		CarInformation.loadCache(appSettings)

		assertEquals(emptyMap<String, String>(), CarInformation.currentCapabilities)
		assertEquals(mapOf("tts" to "true"), CarInformation.cachedCapabilities)
		assertEquals(CarInformation.cachedCapabilities, carInformation.capabilities)
	}
}