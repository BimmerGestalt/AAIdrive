package me.hufman.androidautoidrive.maps

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.CompletableDeferred
import me.hufman.androidautoidrive.carapp.maps.TAG

class GmapKeyValidation(val context: Context) {
	val placesClient: PlacesClient

	init {
		val api_key = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
				.metaData.getString("com.google.android.geo.API_KEY") ?: ""
		Places.initialize(context, api_key)
		placesClient = Places.createClient(context)
	}

	suspend fun validateKey(): Boolean? {
		val result = CompletableDeferred<Boolean?>()
		val autocompleteRequest = FindAutocompletePredictionsRequest.builder()
				.setQuery("Coffee")
				.build()

		placesClient.findAutocompletePredictions(autocompleteRequest).addOnSuccessListener {
			result.complete(true)
		}.addOnFailureListener {
			Log.w(TAG, "Unsuccessful result when validating Google API Key against Google Places: $it")
			if (it is ApiException) {
				if (it.statusCode == 9011) {
					/** This error code is seen for these messages:
					- The provided API key is invalid  (typo)
					- This API key is not authorized to use this service or API.  (missing Places scope)
					*/
					result.complete(false)
				} else {
					result.complete(null)
				}
			} else {
				result.complete(null)
			}
		}
		return result.await()
	}
}