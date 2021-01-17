package me.hufman.androidautoidrive

import org.junit.Assert.assertEquals
import org.junit.Test

class CarInformationTest {
	@Test
	fun testCachedCapabilitiesLayer() {
		val carInformation = CarInformationObserver()
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
		val appSettings = MockAppSettings()
		val carInformation = CarInformationObserver()
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