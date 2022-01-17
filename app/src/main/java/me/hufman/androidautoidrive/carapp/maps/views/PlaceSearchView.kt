package me.hufman.androidautoidrive.carapp.maps.views

import androidx.annotation.VisibleForTesting
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.hufman.androidautoidrive.carapp.InputState
import me.hufman.androidautoidrive.carapp.maps.MapInteractionController
import me.hufman.androidautoidrive.carapp.maps.MapResult
import me.hufman.androidautoidrive.carapp.maps.MapPlaceSearch
import kotlin.coroutines.CoroutineContext

class PlaceSearchView(state: RHMIState, val mapPlaceSearch: MapPlaceSearch, val interaction: MapInteractionController): InputState<MapResult>(state), CoroutineScope {
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.IO

	@VisibleForTesting
	var searchJob: Job? = null

	override fun onEntry(input: String) {
		searchJob?.cancel()
		searchJob = launch {
			val results = mapPlaceSearch.searchLocationsAsync(input).await()
			sendSuggestions(results)
		}
	}

	override fun onSelect(item: MapResult, index: Int) {
		interaction.stopNavigation()
		searchJob?.cancel()
		searchJob = launch {
			val locationResult = if (item.location == null) {
				mapPlaceSearch.resultInformationAsync(item.id).await()    // ask for LatLong, to navigate to
			} else {
				item
			}
			if (locationResult?.location != null) {
				interaction.navigateTo(locationResult.location)
			}
		}
	}
}