package me.hufman.androidautoidrive.maps

import com.google.gson.JsonObject
import com.mapbox.api.geocoding.v5.MapboxGeocoding
import com.mapbox.api.geocoding.v5.models.CarmenFeature
import com.mapbox.api.geocoding.v5.models.GeocodingResponse
import com.mapbox.geojson.Point
import com.nhaarman.mockito_kotlin.*
import io.bimmergestalt.idriveconnectkit.CDSProperty
import kotlinx.coroutines.runBlocking
import me.hufman.androidautoidrive.carapp.CDSDataProvider
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Callback
import retrofit2.Response

class MapboxPlaceSearchTest {

	val searchCallback = argumentCaptor<Callback<GeocodingResponse>>()
	val searchClient = mock<MapboxGeocoding> {
		on {enqueueCall(searchCallback.capture())} doAnswer {}
	}
	val searchBuilder = mock<MapboxGeocoding.Builder> {
		on {query(any<String>())} doAnswer {it.mock as MapboxGeocoding.Builder}
		on {build()} doReturn searchClient
	}
	val cdsData = CDSDataProvider()
	val locationProvider = CdsLocationProvider(cdsData)
	val placeSearch = MapboxPlaceSearch(searchBuilder, locationProvider)

	fun makeFeature(id: String, placeType: String, name: String, address: String, center: Point? = null): CarmenFeature {
		val properties = JsonObject().apply {
			addProperty("address", address)
		}
		return mock {
			on {id()} doReturn id
			on {placeType()} doReturn listOf(placeType)
			on {placeName()} doReturn name
			on {properties()} doReturn properties
			on {center()} doReturn center
		}
	}
	fun makeResponse(vararg results: CarmenFeature): Response<GeocodingResponse> {
		val body = mock<GeocodingResponse> {
			on {features()} doReturn results.toList()
		}
		return mock {
			on {body()} doReturn body
		}
	}

	@Test
	fun testFailResults() {
		val pendingResult = placeSearch.searchLocationsAsync("missing")
		assertFalse(pendingResult.isCompleted)
		verify(searchClient).enqueueCall(any())
		searchCallback.lastValue.onFailure(mock(), Exception())
		assertTrue(pendingResult.isCompleted)
		runBlocking {
			assertEquals(emptyList<MapResult>(), pendingResult.await())
		}
	}

	@Test
	fun testEmptyResults() {
		val pendingResult = placeSearch.searchLocationsAsync("missing")
		assertFalse(pendingResult.isCompleted)
		verify(searchClient).enqueueCall(any())
		searchCallback.lastValue.onResponse(mock(), makeResponse())
		assertTrue(pendingResult.isCompleted)
		runBlocking {
			assertEquals(emptyList<MapResult>(), pendingResult.await())
		}
	}

	@Test
	fun testAddressLookupUnknownLocation() {
		val pendingResult = placeSearch.searchLocationsAsync("123 Test Street")
		assertFalse(pendingResult.isCompleted)
		verify(searchBuilder, never()).proximity(any())
		verify(searchClient).enqueueCall(any())
		searchCallback.lastValue.onResponse(mock(), makeResponse(
				makeFeature("placeID1", "address", "123 Test Street, Somewhere, State", "123 Test Street", Point.fromLngLat(2.0, 1.0))
		))
		assertTrue(pendingResult.isCompleted)
		val results = runBlocking { pendingResult.await() }
		assertEquals(1, results.size)
		assertEquals("placeID1", results[0].id)
		assertEquals("", results[0].name)
		assertEquals("123 Test Street, Somewhere, State", results[0].address)
		assertEquals(LatLong(1.0, 2.0), results[0].location)
		assertNull(results[0].distanceKm)
	}

	@Test
	fun testAddressLookupWithLocation() {
		locationProvider.start()
		cdsData.onPropertyChangedEvent(CDSProperty.NAVIGATION_GPSPOSITION, JsonObject().apply {
			add("GPSPosition", JsonObject().apply {
				addProperty("latitude", 1.1)
				addProperty("longitude", 1.5)
			})
		})

		val pendingResult = placeSearch.searchLocationsAsync("123 Test Street")
		assertFalse(pendingResult.isCompleted)
		verify(searchBuilder).proximity(Point.fromLngLat(1.5, 1.1))
		verify(searchClient).enqueueCall(any())
		searchCallback.lastValue.onResponse(mock(), makeResponse(
				makeFeature("placeID1", "address", "123 Test Street, Somewhere, State", "123 Test Street", Point.fromLngLat(2.0, 1.0))
		))
		assertTrue(pendingResult.isCompleted)
		val results = runBlocking { pendingResult.await() }
		assertEquals(1, results.size)
		assertEquals("placeID1", results[0].id)
		assertEquals("", results[0].name)
		assertEquals("123 Test Street, Somewhere, State", results[0].address)
		assertEquals(LatLong(1.0, 2.0), results[0].location)
		assertEquals(56.74, results[0].distanceKm!!.toDouble(), .01)
	}

	@Test
	fun testPlaceLookupUnknownLocation() {
		val pendingResult = placeSearch.searchLocationsAsync("Coffee")
		assertFalse(pendingResult.isCompleted)
		verify(searchBuilder, never()).proximity(any())
		verify(searchClient).enqueueCall(any())
		searchCallback.lastValue.onResponse(mock(), makeResponse(
				makeFeature("placeID1", "place", "Place Name, 123 Test Street, Somewhere, State", "123 Test Street", Point.fromLngLat(2.0, 1.0))
		))
		assertTrue(pendingResult.isCompleted)
		val results = runBlocking { pendingResult.await() }
		assertEquals(1, results.size)
		assertEquals("placeID1", results[0].id)
		assertEquals("Place Name", results[0].name)
		assertEquals("123 Test Street, Somewhere, State", results[0].address)
		assertEquals(LatLong(1.0, 2.0), results[0].location)
		assertNull(results[0].distanceKm)
	}
}