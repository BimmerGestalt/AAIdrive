package me.hufman.androidautoidrive.maps

import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIAction
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIComponent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIModel
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import io.bimmergestalt.idriveconnectkit.rhmi.mocking.RHMIApplicationMock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import me.hufman.androidautoidrive.carapp.FullImageView
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.maps.MapInteractionController
import me.hufman.androidautoidrive.carapp.maps.views.PlaceSearchView
import me.hufman.androidautoidrive.carapp.maps.views.SearchResultsView
import org.junit.Assert.*
import org.junit.Test

@ExperimentalCoroutinesApi
class PlaceSearchViewTest {
	val mapSearchResults = listOf(
		MapResult("placeID1", "Place Name", "123 Test Street"),
		MapResult("placeID2", "Other Place")
	)
	val locationSearchResult = MapResult("placeID1", "Place Name", "123 Test Street", mock())
	val mapPlaceSearch = mock<MapPlaceSearch> {
		on {searchLocationsAsync(any())} doReturn CompletableDeferred(mapSearchResults)
		on {resultInformationAsync(mapSearchResults[0].id)} doReturn CompletableDeferred(locationSearchResult)
	}
	val mapInteractionController = mock<MapInteractionController>()
	val fullImageView = mock<FullImageView> {
		on {state} doReturn RHMIState.PlainState(mock(), 6)
	}
	val searchResultsView = mock<SearchResultsView> {
		on {state} doReturn RHMIState.PlainState(mock(), 7)
	}

	val mockApp = RHMIApplicationMock()
	val inputState = RHMIState.MockState(mockApp, 5).asPlainState()
	val inputComponent = RHMIComponent.MockComponent(mockApp, 10).asInput().also {
		it.action = 15
		it.suggestAction = 16
		it.resultAction = 17
		it.textModel = 25
		it.suggestModel = 26
		it.resultModel = 27

		inputState.componentsList.add(it)
		mockApp.states[5] = inputState
		mockApp.actions[16] = RHMIAction.HMIAction(mockApp, 16).apply {
			targetModel = 20
		}
		mockApp.models[20] = RHMIModel.RaIntModel(mockApp, 20)
	}

	@Test
	fun testInitialization() = runBlocking {
		val view = PlaceSearchView(inputState, mapPlaceSearch, mapInteractionController)
	}

	@Test
	fun testSearch() = runBlocking {
		val view = PlaceSearchView(inputState, mapPlaceSearch, mapInteractionController)

		view.onInput("voice input")
		view.searchJob?.join()
		assertNotNull(mockApp.modelData[inputComponent.suggestModel])

		val data = mockApp.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable
		assertEquals(1, data.numColumns)
		assertEquals(3, data.numRows)
		assertEquals(L.MAP_SEARCH_RESULTS_VIEW_FULL_RESULTS, data.data[0][0])
		assertEquals("Place Name\n123 Test Street", data.data[1][0])
		assertEquals("Other Place", data.data[2][0])
	}

	@Test
	fun testFullResults() = runBlocking {
		val view = PlaceSearchView(inputState, mapPlaceSearch, mapInteractionController)
		view.initWidgets(fullImageView, searchResultsView)

		view.onInput("voice input")

		view.searchJob?.join()
		assertNotNull(mockApp.modelData[inputComponent.suggestModel])
		val data = mockApp.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable
		assertEquals(L.MAP_SEARCH_RESULTS_VIEW_FULL_RESULTS, data.data[0][0])

		view.onSelect(view.suggestions[0], 0)
		verify(mapInteractionController, never()).stopNavigation()
		view.searchJob?.join()
		verify(mapInteractionController, never()).navigateTo(any())

		assertEquals(searchResultsView.state.id, view.inputComponent.getSuggestAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value)
		verify(searchResultsView).setContents(any())
	}
	@Test
	fun testNavigate() = runBlocking {
		val view = PlaceSearchView(inputState, mapPlaceSearch, mapInteractionController)
		view.initWidgets(fullImageView, searchResultsView)

		view.onInput("voice input")

		view.searchJob?.join()
		assertNotNull(mockApp.modelData[inputComponent.suggestModel])

		view.onSelect(view.suggestions[1], 1)
		verify(mapInteractionController).stopNavigation()
		view.searchJob?.join()

		verify(mapInteractionController).navigateTo(locationSearchResult.location!!)
		assertEquals(fullImageView.state.id, view.inputComponent.getSuggestAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value)
	}
}