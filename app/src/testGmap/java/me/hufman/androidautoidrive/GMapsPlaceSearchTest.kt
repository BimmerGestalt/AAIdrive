package me.hufman.androidautoidrive

import android.text.SpannableString
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.*
import org.mockito.kotlin.*
import kotlinx.coroutines.runBlocking
import me.hufman.androidautoidrive.maps.CarLocationProvider
import me.hufman.androidautoidrive.maps.GMapsPlaceSearch
import me.hufman.androidautoidrive.maps.LatLong
import me.hufman.androidautoidrive.maps.MapResult
import org.junit.Assert.*
import org.junit.Test
import org.mockito.stubbing.OngoingStubbing


private infix fun OngoingStubbing<SpannableString>.doReturn(s: String): OngoingStubbing<SpannableString> = thenAnswer {
	mock<SpannableString> { on {toString()} doReturn s }
}

class GMapsPlaceSearchTest {
	var time = 0L
	val timeProvider: () -> Long = { time }
	val mockLocationProvider = mock<CarLocationProvider>()

	val searchQueryArgs = argumentCaptor<FindAutocompletePredictionsRequest>()
	val searchSuccessListener = argumentCaptor<OnSuccessListener<FindAutocompletePredictionsResponse>>()
	val searchFailureListener = argumentCaptor<OnFailureListener>()
	val autocompleteResultTask = mock<Task<FindAutocompletePredictionsResponse>> {
		on { addOnSuccessListener(searchSuccessListener.capture()) } doAnswer { it.mock as Task<FindAutocompletePredictionsResponse> }
		on { addOnFailureListener(searchFailureListener.capture()) } doAnswer { it.mock as Task<FindAutocompletePredictionsResponse> }
	}
	val placeQueryArgs = argumentCaptor<FetchPlaceRequest>()
	val placeSuccessListener = argumentCaptor<OnSuccessListener<FetchPlaceResponse>>()
	val placeFailureListener = argumentCaptor<OnFailureListener>()
	val placeResultTask = mock<Task<FetchPlaceResponse>> {
		on { addOnSuccessListener(placeSuccessListener.capture()) } doAnswer { it.mock as Task<FetchPlaceResponse> }
		on { addOnFailureListener(placeFailureListener.capture()) } doAnswer { it.mock as Task<FetchPlaceResponse> }
	}

	val mockPlacesClient = mock<PlacesClient> {
		on { findAutocompletePredictions(searchQueryArgs.capture()) } doReturn autocompleteResultTask
		on { fetchPlace(placeQueryArgs.capture()) } doReturn placeResultTask
	}

	val placeSearch = GMapsPlaceSearch(mockPlacesClient, mockLocationProvider, timeProvider)

	@Test
	fun testSearchUnknownLocation(): Unit = runBlocking {
		val results = placeSearch.searchLocationsAsync("query")
		assertEquals(emptyList<MapResult>(), results.await())

		// hard to test the normal function because of creating Location object in unit tests
	}

	@Test
	fun testSearchLocations(): Unit = runBlocking {
		val results = placeSearch.searchLocationsAsync("query", LatLng(1.0, 2.0))
		assertEquals("query", searchQueryArgs.firstValue.query)

		val apiResults = listOf(
				mock<AutocompletePrediction> {
					on { placeId } doReturn "placeID1"
					on { getPrimaryText(anyOrNull()) } doReturn "Place Name"
					on { getSecondaryText(anyOrNull()) } doReturn "123 Test Street"
				}
		)
		val apiResponse = mock<FindAutocompletePredictionsResponse> {
			on { autocompletePredictions } doAnswer { apiResults }
		}
		searchSuccessListener.firstValue.onSuccess(apiResponse)

		val mapResults = results.await()
		assertEquals(1, mapResults.size)
		assertEquals("placeID1", mapResults[0].id)
		assertEquals("Place Name", mapResults[0].name)
		assertEquals("123 Test Street", mapResults[0].address)
	}

	@Test
	fun testSearchLocationsFailed(): Unit = runBlocking {
		val results = placeSearch.searchLocationsAsync("query", LatLng(1.0, 2.0))
		searchFailureListener.firstValue.onFailure(Exception())

		val mapResults = results.await()
		assertEquals(0, mapResults.size)
	}

	@Test
	fun testSessionExpiry() {
		// each invocation checks the timeProvider for the current time
		// and expired tokens check it a second time to save the time
		val results = placeSearch.searchLocationsAsync("query", LatLng(1.0, 2.0))
		searchFailureListener.firstValue.onFailure(Exception())

		time += 5000
		val results1 = placeSearch.searchLocationsAsync("query", LatLng(1.0, 2.0))
		searchFailureListener.firstValue.onFailure(Exception())

		// just about the expire
		time += 180000 - 5000 - 1000
		val results2 = placeSearch.searchLocationsAsync("query", LatLng(1.0, 2.0))
		searchFailureListener.firstValue.onFailure(Exception())

		// now expired
		time += 2000
		val results3 = placeSearch.searchLocationsAsync("query", LatLng(1.0, 2.0))
		searchFailureListener.firstValue.onFailure(Exception())

		assertSame(searchQueryArgs.firstValue.sessionToken, searchQueryArgs.secondValue.sessionToken)
		assertSame(searchQueryArgs.firstValue.sessionToken, searchQueryArgs.thirdValue.sessionToken)
		assertNotSame(searchQueryArgs.firstValue.sessionToken, searchQueryArgs.lastValue.sessionToken)
	}

	@Test
	fun testPlaceLookup() = runBlocking {
		val result = placeSearch.resultInformationAsync("placeID1")
		val apiPlace = mock<Place> {
			on { name } doReturn "Place Name"
			on { address } doReturn "123 Test Street"
			on { latLng } doReturn LatLng(5.0, 6.0)
		}
		val apiResponse = mock<FetchPlaceResponse> {
			on { place } doReturn apiPlace
		}
		placeSuccessListener.firstValue.onSuccess(apiResponse)

		val placeResult = result.await()
		assertEquals("placeID1", placeResult?.id)
		assertEquals("Place Name", placeResult?.name)
		assertEquals("123 Test Street", placeResult?.address)
		assertEquals(LatLong(5.0, 6.0), placeResult?.location)
	}

	@Test
	fun testPlaceLookupFailed() = runBlocking {
		val result = placeSearch.resultInformationAsync("placeID1")
		placeFailureListener.firstValue.onFailure(Exception())

		val placeResult = result.await()
		assertNull(placeResult)
	}

}