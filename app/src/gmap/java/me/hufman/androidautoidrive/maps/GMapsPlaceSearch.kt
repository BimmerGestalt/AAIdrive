package me.hufman.androidautoidrive.maps

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import me.hufman.androidautoidrive.carapp.maps.TAG

class GMapsPlaceSearch(private val placesClient: PlacesClient, private val locationProvider: CarLocationProvider, private val timeProvider: () -> Long = {System.currentTimeMillis()}): MapPlaceSearch {
	companion object {
		private const val SEARCH_SESSION_TTL = 180000L    // number of milliseconds that a search session can live   https://stackoverflow.com/a/52339858
		fun getInstance(context: Context, locationProvider: CarLocationProvider): GMapsPlaceSearch {
			val api_key = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
					.metaData.getString("com.google.android.geo.API_KEY") ?: ""
			Places.initialize(context, api_key)
			val placesClient = Places.createClient(context)
			return GMapsPlaceSearch(placesClient, locationProvider)
		}
	}

	private var searchSessionStart: Long = 0    // when the search session started
	private var searchSessionConsumed = true    // if we used the search session for a Place Info lookup
	private var searchSession: AutocompleteSessionToken? = null // the search session token


	private fun generateSearchSession() {
		// the search session only should live for 3 minutes
		if (searchSessionStart + SEARCH_SESSION_TTL < timeProvider()) {
			searchSession = null
		}
		if (searchSession == null) {
			searchSession = AutocompleteSessionToken.newInstance()
			searchSessionStart = timeProvider()
			searchSessionConsumed = false
		}
	}
	override fun searchLocationsAsync(query: String): Deferred<List<MapResult>> {
		val location = locationProvider.currentLocation ?: return CompletableDeferred(emptyList())
		return searchLocationsAsync(query, LatLng(location.latitude, location.latitude))
	}
	fun searchLocationsAsync(query: String, location: LatLng): Deferred<List<MapResult>> {
		generateSearchSession()

		val bounds = RectangularBounds.newInstance(location, location)
		Log.i(TAG, "Starting Place search for $query near $bounds")
		val autocompleteRequest = FindAutocompletePredictionsRequest.builder()
				.setLocationBias(bounds)
				.setSessionToken(searchSession)
				.setQuery(query)
				.build()
		val results = CompletableDeferred<List<MapResult>>()
		placesClient.findAutocompletePredictions(autocompleteRequest).addOnSuccessListener { result ->
			val autocompleteResults = result?.autocompletePredictions ?: emptyList()
			Log.i(TAG, "Received ${autocompleteResults.size} results for query $query")

			val mapResults = autocompleteResults.map {
				MapResult(it.placeId, it.getPrimaryText(null).toString(), it.getSecondaryText(null).toString())
			}
			results.complete(mapResults)
		}.addOnFailureListener {
			Log.w(TAG, "Unsuccessful result when loading results for $query: $it")
			results.complete(emptyList())
		}
		return results
	}

	override fun resultInformationAsync(resultId: String): Deferred<MapResult?> {
		val requestedFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
		val requestBuilder = FetchPlaceRequest.builder(resultId, requestedFields)
		if (!searchSessionConsumed) {
			requestBuilder.sessionToken = searchSession
			searchSessionConsumed = true
		}
		val request = requestBuilder.build()

		val result = CompletableDeferred<MapResult?>()
		placesClient.fetchPlace(request).addOnSuccessListener { searchResult ->
			Log.i(TAG, "Received Place result for resultId $resultId: ${searchResult?.place}")
			val place = searchResult?.place
			val latLng = place?.latLng
			if (latLng == null) {
				Log.w(TAG, "Place does not have a latlng location!")
				result.complete(null)
				return@addOnSuccessListener
			}
			val mapResult = MapResult(resultId, place.name.toString(), place.address.toString(),
					LatLong(latLng.latitude, latLng.longitude))
			result.complete(mapResult)
		}.addOnFailureListener {
			Log.w(TAG, "Did not find Place info for Place ID $resultId: $it")
			result.complete(null)
		}
		return result
	}
}