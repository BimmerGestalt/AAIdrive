package me.hufman.androidautoidrive.carapp.maps.views

import androidx.annotation.VisibleForTesting
import io.bimmergestalt.idriveconnectkit.rhmi.*
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.carapp.CDSVehicleUnits
import me.hufman.androidautoidrive.carapp.FullImageView
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.maps.MapAppMode
import me.hufman.androidautoidrive.carapp.maps.MapInteractionController
import me.hufman.androidautoidrive.maps.MapPlaceSearch
import me.hufman.androidautoidrive.maps.MapResult
import me.hufman.androidautoidrive.utils.truncate
import kotlin.coroutines.CoroutineContext

class SearchResultsView(val state: RHMIState, val mapPlaceSearch: MapPlaceSearch, val interaction: MapInteractionController, val mapAppMode: MapAppMode): CoroutineScope {
	companion object {
		// current default row width only supports 22 chars before rolling over
		private const val ROW_LINE_MAX_LENGTH = 22

		val emptyList = RHMIModel.RaListModel.RHMIListConcrete(2).apply {
			this.addRow(arrayOf("", L.MAP_SEARCH_RESULTS_EMPTY))
		}
		val searchingList = RHMIModel.RaListModel.RHMIListConcrete(2).apply {
			this.addRow(arrayOf("", L.MAP_SEARCH_RESULTS_SEARCHING))
		}

		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
					state.componentsList.filterIsInstance<RHMIComponent.Label>().isNotEmpty() &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty() &&
					state.componentsList.filterIsInstance<RHMIComponent.Image>().isEmpty()
		}
	}
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.IO

	@VisibleForTesting
	var loaderJob: Job? = null
	@VisibleForTesting
	var searchJob: Job? = null      // to expand search results with missing Locations
	private var loadingContents: Deferred<List<MapResult>> = CompletableDeferred(emptyList())
	private var contents: List<MapResult> = emptyList()
	private val listComponent = state.componentsList.filterIsInstance<RHMIComponent.List>().first()
	private val listModel = listComponent.getModel()!!

	fun initWidgets(fullImageView: FullImageView) {
		state.getTextModel()?.asRaDataModel()?.value = L.MAP_SEARCH_RESULTS_TITLE
		state.focusCallback = FocusCallback {
			if (it) {
				show()
			} else {
				loaderJob?.cancel()
			}
		}

		listComponent.setVisible(true)
		listComponent.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH, "125,*")
		listComponent.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index ->
			onSelected(contents.getOrNull(index))
		}
		listComponent.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = fullImageView.state.id
	}
	fun setContents(loadingContents: Deferred<List<MapResult>>) {
		loaderJob?.cancel()
		this.loadingContents = loadingContents
	}

	fun show() {
		loaderJob?.cancel()
		loaderJob = launch {
			if (!loadingContents.isCompleted) {
				contents = emptyList()
				listComponent.setEnabled(false)
				listModel.value = searchingList
			}
			contents = loadingContents.await()
			if (contents.isEmpty()) {
				listComponent.setEnabled(false)
				listModel.value = emptyList
			} else {
				listComponent.setEnabled(true)
				listModel.value = MapResultListAdapter(mapAppMode, contents)
			}
		}
	}

	class MapResultListAdapter(mapAppMode: MapAppMode, contents: List<MapResult>): RHMIModel.RaListModel.RHMIListAdapter<MapResult>(2, contents) {
		val distanceUnits = mapAppMode.distanceUnits        // cache across each row for this set of results
		override fun convertRow(index: Int, item: MapResult): Array<Any> {
			val distance = item.distanceKm?.let {
				val label = if (distanceUnits == CDSVehicleUnits.Distance.Miles) "mi" else "km"
				"${distanceUnits.fromCarUnit(it).toInt()} $label"
			}
			val title = "${item.name.truncate(ROW_LINE_MAX_LENGTH)}\n${item.address ?: ""}"

			return arrayOf(distance ?: "", title)
		}
	}

	fun onSelected(result: MapResult?) {
		if (result == null) {
			throw RHMIActionAbort()
		}
		searchJob?.cancel()
		searchJob = launch {
			val locationResult = if (result.location == null) {
				mapPlaceSearch.resultInformationAsync(result.id).await()    // ask for LatLong, to navigate to
			} else {
				result
			}
			if (locationResult?.location != null) {
				interaction.navigateTo(locationResult.location)
				// HMIAction is set up already
			}
		}
	}
}