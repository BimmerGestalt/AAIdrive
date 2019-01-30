package me.hufman.androidautoidrive

import android.content.IntentFilter
import android.support.test.InstrumentationRegistry
import android.support.test.annotation.UiThreadTest
import android.support.test.runner.AndroidJUnit4
import com.google.android.gms.maps.model.LatLng
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyArray
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import junit.framework.Assert.assertEquals
import me.hufman.androidautoidrive.carapp.maps.*
import org.awaitility.Awaitility.await
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

	@Before
	fun setUp() {
		AppSettings.loadDefaultSettings()
	}

	@Test
	fun queryFromCar() {
		val appContext = InstrumentationRegistry.getTargetContext()
		val listener = MapsInteractionControllerListener(appContext, interactionReceiver)
		listener.onCreate()
		MapInteractionControllerIntent(appContext).searchLocations("test")
		await().untilAsserted { verify(interactionReceiver).searchLocations("test") }

		MapInteractionControllerIntent(appContext).resultInformation("details")
		await().untilAsserted { verify(interactionReceiver).resultInformation("details") }
	}

	@Test
	fun queryResultsToCar() {
		val appContext = InstrumentationRegistry.getTargetContext()
		val listener = MapView.MapResultsReceiver(mockResultsReceiver)
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
		val appContext = InstrumentationRegistry.getTargetContext()
		val virtualDisplay = VirtualDisplayScreenCapture(appContext)
		val mapController = GMapsController(appContext, mockResultsReceiver, virtualDisplay)
		mapController.searchLocations("test")
		await().untilAsserted { verify(mockResultsReceiver).onSearchResults(anyArray()) }

		mapController.resultInformation("ChIJDflB7BWuEmsRYPbx-Wh9AQ8")
		await().untilAsserted { verify(mockResultsReceiver).onPlaceResult(any()) }
	}

	@Test
	@UiThreadTest
	fun testNavigation() {
		val appContext = InstrumentationRegistry.getTargetContext()
		val virtualDisplay = VirtualDisplayScreenCapture(appContext)
		val mapController = GMapsController(appContext, mockResultsReceiver, virtualDisplay)
		mapController.showMap()
		await().until { mapController.projection != null }
		mapController.projection?.lastLocation = LatLng(37.389444, -122.081944)
		mapController.navigateTo(LatLong(37.429167, -122.138056))
		await().until { mapController.currentNavRoute != null }
	}
}