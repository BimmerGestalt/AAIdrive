package me.hufman.androidautoidrive.maps

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIComponent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import io.bimmergestalt.idriveconnectkit.rhmi.mocking.RHMIApplicationMock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import me.hufman.androidautoidrive.carapp.maps.MapInteractionController
import me.hufman.androidautoidrive.carapp.maps.views.PlaceSearchView
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
		assertEquals(2, data.numRows)
		assertEquals("Place Name\n123 Test Street", data.data[0][0])
		assertEquals("Other Place", data.data[1][0])
	}

	@Test
	fun testNavigate() = runBlocking {
		val view = PlaceSearchView(inputState, mapPlaceSearch, mapInteractionController)

		view.onInput("voice input")

		view.searchJob?.join()
		assertNotNull(mockApp.modelData[inputComponent.suggestModel])

		view.onSelect(mapSearchResults[0], 0)
		verify(mapInteractionController).stopNavigation()
		view.searchJob?.join()

		verify(mapInteractionController).navigateTo(locationSearchResult.location!!)
	}
}