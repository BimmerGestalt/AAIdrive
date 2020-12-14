package me.hufman.androidautoidrive.carapp.maps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.Serializable


data class LatLong(val latitude: Double, val longitude: Double): Serializable
data class MapResult(val id: String, val name: String,
                val address: String? = null,
                val location: LatLong? = null): Serializable {
	override fun toString(): String {
		if (address != null) {
			return "$name\n$address"
		}
		return name
	}
}

const val INTENT_MAP_RESULT = "me.hufman.androidautoidrive.maps.INTENT_RESULT"
const val INTENT_MAP_RESULTS = "me.hufman.androidautoidrive.maps.INTENT_RESULTS"
const val EXTRA_MAP_RESULT = "me.hufman.androidautoidrive.maps.EXTRA_RESULT"
const val EXTRA_MAP_RESULTS = "me.hufman.androidautoidrive.maps.EXTRA_RESULTS"
interface MapResultsController {
	/** Initial search results */
	fun onSearchResults(results: Array<MapResult>)
	/** Further details about the result */
	fun onPlaceResult(result: MapResult)
}

class MapResultsSender(val context: Context): MapResultsController {
	override fun onSearchResults(results: Array<MapResult>) {
		val intent = Intent(INTENT_MAP_RESULTS).putExtra(EXTRA_MAP_RESULTS, results)
				.setPackage(context.packageName)
		context.sendBroadcast(intent)
	}

	override fun onPlaceResult(result: MapResult) {
		val intent = Intent(INTENT_MAP_RESULT).putExtra(EXTRA_MAP_RESULT, result)
				.setPackage(context.packageName)
		context.sendBroadcast(intent)
	}
}

class MapResultsReceiver(val controller: MapResultsController): BroadcastReceiver() {
	override fun onReceive(context: Context?, intent: Intent?) {
		if (context?.packageName == null || intent?.`package` == null || context.packageName != intent.`package`) return
		if (intent.action == INTENT_MAP_RESULTS) {
			val results = intent.getSerializableExtra(EXTRA_MAP_RESULTS) as? Array<*>
			val mapResults = results?.filterIsInstance<MapResult>()?.toTypedArray() ?: return
			controller.onSearchResults(mapResults)
		}
		if (intent.action == INTENT_MAP_RESULT) {
			val mapResult = intent.getSerializableExtra(EXTRA_MAP_RESULT) as? MapResult ?: return
			controller.onPlaceResult(mapResult)
		}
	}
}