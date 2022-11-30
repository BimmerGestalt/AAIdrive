package me.hufman.androidautoidrive.maps

import android.location.Location
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.rhmi.*
import io.bimmergestalt.idriveconnectkit.rhmi.mocking.RHMIApplicationMock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import me.hufman.androidautoidrive.carapp.CDSVehicleUnits
import me.hufman.androidautoidrive.carapp.FullImageView
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.maps.MapAppMode
import me.hufman.androidautoidrive.carapp.maps.MapInteractionController
import me.hufman.androidautoidrive.carapp.maps.views.SearchResultsView
import org.awaitility.Awaitility.await
import org.junit.Assert.*
import org.junit.Test

class SearchResultsViewTest {
	val mockApp = RHMIApplicationMock()
	val searchResultsState = RHMIState.MockState(mockApp, 5).asPlainState().also {
		it.textModel = 20
		mockApp.models[20] = RHMIModel.RaDataModel(mockApp, 20)
	}
	val searchResultsComponent = RHMIComponent.MockComponent(mockApp, 15).asList().also {
		it.model = 25
		it.action = 26

		searchResultsState.componentsList.add(it)
		mockApp.states[5] = searchResultsState
		mockApp.models[25] = RHMIModel.RaListModel(mockApp, 25)
		mockApp.actions[26] = RHMIAction.CombinedAction(mockApp, 26,
			RHMIAction.RAAction(mockApp, 27),
			RHMIAction.HMIAction(mockApp, 28).apply {
				targetModel = 29
				mockApp.models[29] = RHMIModel.RaIntModel(mockApp, 29)
			}
		)
	}
	val mapPlaceSearch = mock<MapPlaceSearch>()
	val mapInteractionController = mock<MapInteractionController>()
	val mapAppMode = mock<MapAppMode> {
		on {distanceUnits} doReturn CDSVehicleUnits.Distance.Kilometers
	}
	val locationProvider = mock<CarLocationProvider> {
		on {currentLocation} doReturn null
	}
	val fullImageView = mock<FullImageView> {
		on {state} doReturn RHMIState.PlainState(mockApp, 6)
	}

	val view = SearchResultsView(searchResultsState, mapPlaceSearch, mapInteractionController, mapAppMode, locationProvider).apply {
		initWidgets(fullImageView)
	}

	@Test
	fun testInit() {
		assertEquals(L.MAP_SEARCH_RESULTS_TITLE, searchResultsState.getTextModel()?.asRaDataModel()?.value)
		assertNotNull(searchResultsState.focusCallback)
		assertNotNull(searchResultsComponent.getAction()?.asRAAction()?.rhmiActionCallback)
		assertEquals(6, searchResultsComponent.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value)
		assertEquals(true, mockApp.propertyData[searchResultsComponent.id]?.get(RHMIProperty.PropertyId.VISIBLE.id))
		assertEquals("125,*", mockApp.propertyData[searchResultsComponent.id]?.get(RHMIProperty.PropertyId.LIST_COLUMNWIDTH.id))
	}

	@Test
	fun testShowEmpty() = runBlocking {
		val results = CompletableDeferred<List<MapResult>>(emptyList())
		view.setContents(results)
		searchResultsState.onHmiEvent(1, mapOf(4.toByte() to true))

		await().until { (mockApp.modelData[25] as? BMWRemoting.RHMIDataTable)?.totalRows == 1 }
		assertEquals(false, mockApp.propertyData[searchResultsComponent.id]?.get(RHMIProperty.PropertyId.ENABLED.id))
		val data = mockApp.modelData[25] as BMWRemoting.RHMIDataTable
		assertEquals(2, data.totalColumns)
		assertEquals(1, data.totalRows)
		assertEquals("", data.data[0][0])
		assertEquals(L.MAP_SEARCH_RESULTS_EMPTY, data.data[0][1])
	}

	@Test
	fun testShowLoading() = runBlocking {
		val results = CompletableDeferred<List<MapResult>>()
		view.setContents(results)
		searchResultsState.onHmiEvent(1, mapOf(4.toByte() to true))

		await().until { (mockApp.modelData[25] as? BMWRemoting.RHMIDataTable)?.totalRows == 1 }
		assertEquals(false, mockApp.propertyData[searchResultsComponent.id]?.get(RHMIProperty.PropertyId.ENABLED.id))
		val data = mockApp.modelData[25] as BMWRemoting.RHMIDataTable
		assertEquals(2, data.totalColumns)
		assertEquals(1, data.totalRows)
		assertEquals("", data.data[0][0])
		assertEquals(L.MAP_SEARCH_RESULTS_SEARCHING, data.data[0][1])
	}

	@Test
	fun testShowResults() = runBlocking {
		val results = CompletableDeferred<List<MapResult>>()
		view.setContents(results)
		searchResultsState.onHmiEvent(1, mapOf(4.toByte() to true))

		await().until { (mockApp.modelData[25] as? BMWRemoting.RHMIDataTable)?.totalRows == 1 }
		assertEquals(false, mockApp.propertyData[searchResultsComponent.id]?.get(RHMIProperty.PropertyId.ENABLED.id))
		val data = mockApp.modelData[25] as BMWRemoting.RHMIDataTable
		assertEquals(2, data.totalColumns)
		assertEquals(1, data.totalRows)
		assertEquals("", data.data[0][0])
		assertEquals(L.MAP_SEARCH_RESULTS_SEARCHING, data.data[0][1])

		results.complete(listOf(
				MapResult("1", "Coffee Corner", "123 Main St"),
				MapResult("2", "Cuppa", "404 Somewhere Blvd", LatLong(1.0, 2.0), 50f),
				MapResult("3", "Coffee and Cream", null),
		))
		await().until { (mockApp.modelData[25] as? BMWRemoting.RHMIDataTable)?.totalRows == 3 }
		val doneData = mockApp.modelData[25] as BMWRemoting.RHMIDataTable
		assertEquals(2, doneData.totalColumns)
		assertEquals(3, doneData.totalRows)
		assertEquals("", doneData.data[0][0])
		assertEquals("Coffee Corner\n123 Main St", doneData.data[0][1])
		assertEquals("50 km\n", doneData.data[1][0])
		assertEquals("Cuppa\n404 Somewhere Blvd", doneData.data[1][1])
		assertEquals("", doneData.data[2][0])
		assertEquals("Coffee and Cream\n", doneData.data[2][1])
	}

	@Test
	fun testShowResultsMiles() = runBlocking {
		whenever(mapAppMode.distanceUnits) doReturn CDSVehicleUnits.Distance.Miles
		val results = CompletableDeferred(listOf(
				MapResult("1", "Coffee Corner", "123 Main St"),
				MapResult("2", "Cuppa", "404 Somewhere Blvd", LatLong(1.0, 2.0), 50f),
		))
		view.setContents(results)
		searchResultsState.onHmiEvent(1, mapOf(4.toByte() to true))

		await().until { (mockApp.modelData[25] as? BMWRemoting.RHMIDataTable)?.totalRows == 2 }
		val doneData = mockApp.modelData[25] as BMWRemoting.RHMIDataTable
		assertEquals(2, doneData.totalColumns)
		assertEquals(2, doneData.totalRows)
		assertEquals("", doneData.data[0][0])
		assertEquals("Coffee Corner\n123 Main St", doneData.data[0][1])
		assertEquals("31 mi\n", doneData.data[1][0])
		assertEquals("Cuppa\n404 Somewhere Blvd", doneData.data[1][1])
	}

	@Test
	fun testBearingArrow() {
		val bearingArrow = {angle: Float? -> SearchResultsView.MapResultListAdapter.bearingArrow(angle) }
		assertEquals("", bearingArrow(null))
		assertEquals("↑", bearingArrow(0f))
		assertEquals("↑", bearingArrow(359f))
		assertEquals("↖", bearingArrow(330f))
	}

	@Test
	fun testShowResultsDirection() = runBlocking {
		val location = mock<Location> {
			on {latitude} doReturn 37.3728018
			on {longitude} doReturn -122.2072274
			on {bearing} doReturn 310f       // car bearing NE
		}
		whenever(locationProvider.currentLocation) doReturn location
		val results = CompletableDeferred(listOf(
				MapResult("1", "Coffee Corner", "123 Main St", LatLong(37.3866955, -122.2653444), 5f),
				MapResult("2", "Cuppa", "404 Somewhere Blvd", LatLong(37.3526909, -122.0513095), 50f),
		))
		view.setContents(results)
		searchResultsState.onHmiEvent(1, mapOf(4.toByte() to true))

		await().until { (mockApp.modelData[25] as? BMWRemoting.RHMIDataTable)?.totalRows == 2 }
		val doneData = mockApp.modelData[25] as BMWRemoting.RHMIDataTable
		assertEquals(2, doneData.totalColumns)
		assertEquals(2, doneData.totalRows)
		assertEquals("5 km\n↖", doneData.data[0][0])
		assertEquals("Coffee Corner\n123 Main St", doneData.data[0][1])
		assertEquals("50 km\n↘", doneData.data[1][0])       // rotated arrow because of car heading
		assertEquals("Cuppa\n404 Somewhere Blvd", doneData.data[1][1])
	}

	@Test
	fun testClickEmpty() = runBlocking {
		val results = CompletableDeferred<List<MapResult>>(emptyList())
		view.setContents(results)
		searchResultsState.onHmiEvent(1, mapOf(4.toByte() to true))

		await().until { (mockApp.modelData[25] as? BMWRemoting.RHMIDataTable)?.totalRows == 1 }

		try {
			searchResultsComponent.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))
			fail("Was supposed to abort this action")
		} catch (e: RHMIActionAbort) {}
	}

	@Suppress("DeferredResultUnused")
	@Test
	fun testClickPartialResult() = runBlocking {
		val results = CompletableDeferred(listOf(
				MapResult("1", "Coffee Corner", "123 Main St"),
				MapResult("2", "Cuppa", "404 Somewhere Blvd", LatLong(1.0, 2.0), 50f),
		))
		view.setContents(results)
		searchResultsState.onHmiEvent(1, mapOf(4.toByte() to true))

		await().until { (mockApp.modelData[25] as? BMWRemoting.RHMIDataTable)?.totalRows == 2 }

		val singleResult = CompletableDeferred<MapResult>()
		whenever(mapPlaceSearch.resultInformationAsync(any())) doReturn singleResult
		searchResultsComponent.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))

		await().untilAsserted { verify(mapPlaceSearch).resultInformationAsync("1") }
		singleResult.complete(MapResult("1", "Coffee Corner", "123 Main St", LatLong(55.0, -70.0)))

		await().untilAsserted { verify(mapInteractionController).navigateTo(LatLong(55.0, -70.0)) }
	}

	@Test
	fun testClickFullResult() = runBlocking {
		val results = CompletableDeferred(listOf(
				MapResult("1", "Coffee Corner", "123 Main St"),
				MapResult("2", "Cuppa", "404 Somewhere Blvd", LatLong(1.0, 2.0), 50f),
		))
		view.setContents(results)
		searchResultsState.onHmiEvent(1, mapOf(4.toByte() to true))

		await().until { (mockApp.modelData[25] as? BMWRemoting.RHMIDataTable)?.totalRows == 2 }
		searchResultsComponent.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 1))

		await().untilAsserted { verify(mapInteractionController).navigateTo(LatLong(1.0, 2.0)) }
	}
}