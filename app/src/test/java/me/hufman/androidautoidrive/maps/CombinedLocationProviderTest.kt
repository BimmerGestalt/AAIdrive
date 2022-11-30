package me.hufman.androidautoidrive.maps

import android.location.Location
import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CombinedLocationProviderTest(private val preferPhoneLocation: Boolean) {
	companion object {
		@JvmStatic
		@Parameterized.Parameters
		fun data() = listOf(false, true)
	}

	val appSettings = mock<AppSettings> {
		on {this[AppSettings.KEYS.MAP_USE_PHONE_GPS]} doReturn preferPhoneLocation.toString()
	}

	@Test
	fun testSingleStart() {
		val phoneLocationProvider = mock<CarLocationProvider>()
		val carLocationProvider = mock<CarLocationProvider>()
		val combinedLocationProvider = CombinedLocationProvider(appSettings, phoneLocationProvider, carLocationProvider)

		verify(phoneLocationProvider).callback = any()
		verify(carLocationProvider).callback = any()

		combinedLocationProvider.start()
		if (preferPhoneLocation) {
			verify(phoneLocationProvider, times(1)).start()
			verify(carLocationProvider, times(0)).start()
		} else {
			verify(carLocationProvider, times(1)).start()
			verify(phoneLocationProvider, times(0)).start()
		}

		combinedLocationProvider.stop()
		verify(phoneLocationProvider, times(1)).stop()
		verify(carLocationProvider, times(1)).stop()
	}

	@Test
	fun testCallback() {
		val phoneLocationProvider = MockLocationProvider()
		val carLocationProvider = MockLocationProvider()
		val combinedLocationProvider = CombinedLocationProvider(appSettings, phoneLocationProvider, carLocationProvider)
		combinedLocationProvider.callback = mock()

		assertNotNull(phoneLocationProvider.callback)
		assertNotNull(carLocationProvider.callback)

		// referencing a Location is buggy in unit tests, try cleaning and rebuilding after modifying the test
		// if it fails to find the class reference
		val firstLocation = mock<Location>()
		phoneLocationProvider.callback?.invoke(firstLocation)
		assertEquals(firstLocation, combinedLocationProvider.currentLocation)
		verify(combinedLocationProvider.callback)!!.invoke(firstLocation)

		val secondLocation = mock<Location>()
		carLocationProvider.callback?.invoke(secondLocation)
		assertEquals(secondLocation, combinedLocationProvider.currentLocation)
		verify(combinedLocationProvider.callback)!!.invoke(secondLocation)

		verifyNoMoreInteractions(combinedLocationProvider.callback)
	}
}

class MockLocationProvider(): CarLocationProvider() {
	override fun start() {}
	override fun stop() {}
}