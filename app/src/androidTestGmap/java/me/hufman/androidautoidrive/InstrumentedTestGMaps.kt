package me.hufman.androidautoidrive

import android.content.Context
import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.maps.model.LatLng
import com.nhaarman.mockito_kotlin.*
import io.bimmergestalt.idriveconnectkit.RHMIDimensions
import kotlinx.coroutines.runBlocking
import me.hufman.androidautoidrive.carapp.CDSDataProvider
import me.hufman.androidautoidrive.carapp.maps.*
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.maps.CarLocationProvider
import me.hufman.androidautoidrive.maps.GMapsPlaceSearch
import me.hufman.androidautoidrive.maps.LatLong
import org.awaitility.Awaitility.await
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstrumentedTestGMaps {
	val currentLocation = mock<Location> {
		on { latitude } doReturn 37.389444
		on { longitude } doReturn -122.081944
	}
	val locationProvider = mock<CarLocationProvider> {
		on {currentLocation} doReturn currentLocation
	}
	val interactionReceiver = mock<MapInteractionController> {
	}


	fun getContext(): Context {
		return InstrumentationRegistry.getInstrumentation().targetContext
	}
	@Before
	fun setUp() {
		AppSettings.loadDefaultSettings()
	}

	@Test
	fun testGmapSearch() {
		val appContext = InstrumentationRegistry.getInstrumentation().targetContext
		val search = GMapsPlaceSearch.getInstance(appContext, locationProvider)
		runBlocking {
			val results = search.searchLocationsAsync("test", LatLng(37.333, -122.416)).await()

			val info = search.resultInformationAsync("ChIJDflB7BWuEmsRYPbx-Wh9AQ8").await()
			assertNotNull(info)
		}
	}

	@Test
	fun testNavigation() {
		val appContext = InstrumentationRegistry.getInstrumentation().targetContext
		val mapAppMode = MapAppMode.build(RHMIDimensions.create(emptyMap()), MutableAppSettingsReceiver(appContext), CDSDataProvider(), MusicAppMode.TRANSPORT_PORTS.BT)
		val imageCapture = VirtualDisplayScreenCapture.build(mapAppMode)
		val virtualDisplay = VirtualDisplayScreenCapture.createVirtualDisplay(getContext(), imageCapture.imageCapture)
		val mapController = GMapsController(appContext, locationProvider, virtualDisplay, MutableAppSettingsReceiver(appContext), mapAppMode)
		runOnUiThread {
			mapController.showMap()
		}
		await().until { mapController.projection?.map != null }
		runOnUiThread {
			mapController.currentLocation = mock<Location> {
				on { latitude } doReturn 37.389444
				on { longitude } doReturn -122.081944
			}
			mapController.navigateTo(LatLong(37.429167, -122.138056))
		}
		await().until { mapController.navController.currentNavRoute != null }
		imageCapture.onDestroy()
		virtualDisplay.release()
	}
}