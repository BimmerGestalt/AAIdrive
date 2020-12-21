package me.hufman.androidautoidrive

import android.content.Context
import android.content.IntentFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyArray
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import me.hufman.androidautoidrive.carapp.maps.*
import org.awaitility.Awaitility.await
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor

@RunWith(AndroidJUnit4::class)
class InstrumentedTestGMaps {
	val interactionReceiver = mock<MapInteractionController> {
	}

	val mockResultsReceiver = mock<MapResultsController> {

	}

	fun getContext(): Context {
		return InstrumentationRegistry.getInstrumentation().targetContext
	}
	@Before
	fun setUp() {
		AppSettings.loadDefaultSettings()
	}

	@Test
	fun queryFromCar() {
		val appContext = InstrumentationRegistry.getInstrumentation().targetContext
		val listener = MapsInteractionControllerListener(appContext, interactionReceiver)
		listener.onCreate()
		MapInteractionControllerIntent(appContext).searchLocations("test")
		await().untilAsserted { verify(interactionReceiver).searchLocations("test") }

		MapInteractionControllerIntent(appContext).resultInformation("details")
		await().untilAsserted { verify(interactionReceiver).resultInformation("details") }
	}

	@Test
	fun queryResultsToCar() {
		val appContext = InstrumentationRegistry.getInstrumentation().targetContext
		val listener = MapResultsReceiver(mockResultsReceiver)
		appContext.registerReceiver(listener, IntentFilter(INTENT_MAP_RESULTS))
		appContext.registerReceiver(listener, IntentFilter(INTENT_MAP_RESULT))

		MapResultsSender(appContext).onSearchResults(arrayOf(MapResult("test", "Test Name")))
		val destinationResults = ArgumentCaptor.forClass(Array<MapResult>::class.java)
		await().untilAsserted { verify(mockResultsReceiver).onSearchResults(destinationResults.capture() ?: arrayOf()) }
		assertEquals(1, destinationResults.value.size)
		assertEquals(MapResult("test", "Test Name"), destinationResults.value[0])

		MapResultsSender(appContext).onPlaceResult(MapResult("test", "Test Name 2"))
		val destinationResult = ArgumentCaptor.forClass(MapResult::class.java)
		await().untilAsserted { verify(mockResultsReceiver).onPlaceResult(destinationResult.capture() ?: MapResult("null", "Null")) }
		assertEquals(MapResult("test", "Test Name 2"), destinationResult.value)
	}

	@Test
	fun testMapSearch() {
		val appContext = InstrumentationRegistry.getInstrumentation().targetContext
		val imageCapture = VirtualDisplayScreenCapture.build(1000, 400)
		val virtualDisplay = VirtualDisplayScreenCapture.createVirtualDisplay(getContext(), imageCapture.imageCapture)
		val mapController = GMapsController(appContext, mockResultsReceiver, virtualDisplay, MutableAppSettingsReceiver(appContext))
		mapController.searchLocations("test", LatLngBounds(LatLng(37.333, -122.416), LatLng(37.783, -121.9)))
		await().untilAsserted { verify(mockResultsReceiver).onSearchResults(anyArray()) }

		mapController.resultInformation("ChIJDflB7BWuEmsRYPbx-Wh9AQ8")
		await().untilAsserted { verify(mockResultsReceiver).onPlaceResult(any()) }
		imageCapture.onDestroy()
		virtualDisplay.release()
	}

	@Test
	fun testNavigation() {
		val appContext = InstrumentationRegistry.getInstrumentation().targetContext
		val imageCapture = VirtualDisplayScreenCapture.build(1000, 400)
		val virtualDisplay = VirtualDisplayScreenCapture.createVirtualDisplay(getContext(), imageCapture.imageCapture)
		val mapController = GMapsController(appContext, mockResultsReceiver, virtualDisplay, MutableAppSettingsReceiver(appContext))
		runOnUiThread {
			mapController.showMap()
		}
		await().until { mapController.projection?.map != null }
		runOnUiThread {
			mapController.currentLocation = LatLng(37.389444, -122.081944)
			mapController.navigateTo(LatLong(37.429167, -122.138056))
		}
		await().until { mapController.currentNavRoute != null }
		imageCapture.onDestroy()
		virtualDisplay.release()
	}
}