package me.hufman.androidautoidrive.carapp.maps.views

import androidx.annotation.VisibleForTesting
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.carapp.FullImageView
import me.hufman.androidautoidrive.carapp.InputState
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.maps.MapInteractionController
import me.hufman.androidautoidrive.maps.MapResult
import me.hufman.androidautoidrive.maps.MapPlaceSearch
import kotlin.coroutines.CoroutineContext

class PlaceSearchView(state: RHMIState, val mapPlaceSearch: MapPlaceSearch, val interaction: MapInteractionController): InputState<MapResult>(state), CoroutineScope {
	private val SEARCHRESULT_VIEW_FULL_RESULTS = MapResult("__VIEWFULLRESULTS__", name=L.MAP_SEARCH_RESULTS_VIEW_FULL_RESULTS)

	override val coroutineContext: CoroutineContext
		get() = Dispatchers.IO

	@VisibleForTesting
	var searchJob: Job? = null
	private var searchResults: Deferred<List<MapResult>> = CompletableDeferred(emptyList())

	private var fullImageView: FullImageView? = null
	private var searchResultsView: SearchResultsView? = null
	fun initWidgets(fullImageView: FullImageView, searchResultsView: SearchResultsView) {
		this.fullImageView = fullImageView
		this.searchResultsView = searchResultsView
	}

	override fun onEntry(input: String) {
		searchJob?.cancel()
		searchJob = launch {
			this@PlaceSearchView.searchResults = mapPlaceSearch.searchLocationsAsync(input)
			val results = searchResults.await()
			sendSuggestions(results)
		}
	}

	override fun sendSuggestions(newSuggestions: List<MapResult>) {
		val fullSuggestions = if (newSuggestions.isNotEmpty()) {
			listOf(SEARCHRESULT_VIEW_FULL_RESULTS) + newSuggestions
		} else {
			newSuggestions
		}
		super.sendSuggestions(fullSuggestions)
	}

	override fun onSelect(item: MapResult, index: Int) {
		if (item == SEARCHRESULT_VIEW_FULL_RESULTS) {
			inputComponent.getSuggestAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = searchResultsView?.state?.id ?: 0
			searchResultsView?.setContents(searchResults)
			return
		}
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
				inputComponent.getSuggestAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = fullImageView?.state?.id ?: 0
			}
		}
	}

	override fun onOk() {
		inputComponent.getResultAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = searchResultsView?.state?.id ?: 0
		searchResultsView?.setContents(searchResults)
	}
}