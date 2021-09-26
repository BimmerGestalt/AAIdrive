package me.hufman.androidautoidrive.phoneui

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.gson.JsonObject
import com.nhaarman.mockito_kotlin.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.CoroutineTestRule
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.CDSDataProvider
import me.hufman.androidautoidrive.carapp.navigation.NavigationParser
import me.hufman.androidautoidrive.carapp.navigation.NavigationTrigger
import me.hufman.androidautoidrive.phoneui.controllers.NavigationSearchController
import me.hufman.androidautoidrive.phoneui.viewmodels.NavigationStatusModel
import io.bimmergestalt.idriveconnectkit.CDS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class NavigationSearchControllerTest {
	@Rule
	@JvmField
	val instantTaskExecutorRule = InstantTaskExecutorRule()

	@Rule
	@JvmField
	val coroutineTestRule = CoroutineTestRule()

	val context = mock<Context> {
		on { getString(any()) } doReturn "test"
	}
	val parser = mock<NavigationParser>()
	val navigationTrigger = mock<NavigationTrigger>()
	val cdsData = CDSDataProvider()
	val carInformation = mock<CarInformation> {
		on { cdsData } doReturn cdsData
	}

	@Test
	fun testBadSearch() = coroutineTestRule.testDispatcher.runBlockingTest {
		val model = NavigationStatusModel(carInformation)
		val controller = NavigationSearchController(this, parser, navigationTrigger, model, coroutineTestRule.testDispatcherProvider)
		whenever(parser.parseUrl(any())) doAnswer {
			// when the parseUrl is called, the label should say
			context.run(model.searchStatus.value!!)
			verify(context).getString(R.string.lbl_navigation_listener_searching)
			clearInvocations(context)
			assertEquals(true, model.isSearching.value)
			assertEquals(false, model.searchFailed.value)
			// then return the address
			null
		}
		controller.query = "test address"
		controller.startNavigation()
		verify(parser, times(2)).parseUrl("geo:0,0?q=test+address")

		// show an error
		assertEquals(false, model.isSearching.value)
		assertEquals(true, model.searchFailed.value)
		context.run(model.searchStatus.value!!)
		verify(context).getString(R.string.lbl_navigation_listener_parsefailure)
		clearInvocations(context)

		// hide the text after a timeout
		advanceTimeBy(NavigationSearchController.SUCCESS)
		assertEquals("", context.run(model.searchStatus.value!!))
		verify(context, never()).getString(any())

		// changing the input text should clear the error
		controller.query = "changed"
		assertEquals(false, model.searchFailed.value)
	}

	@Test
	fun testRetries() = coroutineTestRule.testDispatcher.runBlockingTest {
		val model = NavigationStatusModel(carInformation)
		val controller = NavigationSearchController(this, parser, navigationTrigger, model, coroutineTestRule.testDispatcherProvider)

		// it should retry parseUrl once if the first result is null
		whenever(parser.parseUrl(any())) doReturn listOf(null, "rhmiCommandString")
		controller.startNavigation("test address")

		// should now be trying to send to the car
		assertEquals(true, model.isSearching.value)
		assertEquals(false, model.searchFailed.value)
		context.run(model.searchStatus.value!!)
		verify(context).getString(R.string.lbl_navigation_listener_pending)
		clearInvocations(context)
		verify(navigationTrigger).triggerNavigation("rhmiCommandString")
		clearInvocations(navigationTrigger)

		// the car missed it the first time, try to send again
		advanceTimeBy(NavigationSearchController.TIMEOUT + 100)
		verify(navigationTrigger).triggerNavigation("rhmiCommandString")
		clearInvocations(navigationTrigger)

		// now wait for the car to notice
		advanceTimeBy(NavigationSearchController.TIMEOUT / 2)
		cdsData.onPropertyChangedEvent(CDS.NAVIGATION.GUIDANCESTATUS, JsonObject().apply { addProperty("guidanceStatus", 1) })

		// wait up to 1000ms for the poll loop
		advanceTimeBy(1000)

		// UI should update with success
		assertEquals(false, model.isSearching.value)
		context.run(model.searchStatus.value!!)
		verify(context).getString(R.string.lbl_navigation_listener_success)
		clearInvocations(context)

		// hide the text after a timeout
		advanceTimeBy(NavigationSearchController.SUCCESS)
		assertEquals("", context.run(model.searchStatus.value!!))
		verify(context, never()).getString(any())
	}

	@Test
	fun testUnsuccess() = coroutineTestRule.testDispatcher.runBlockingTest {
		val model = NavigationStatusModel(carInformation)
		val controller = NavigationSearchController(this, parser, navigationTrigger, model, coroutineTestRule.testDispatcherProvider)

		// it should retry parseUrl once if the first result is null
		whenever(parser.parseUrl(any())) doReturn listOf(null, "rhmiCommandString")
		controller.startNavigation("test address")

		// should now be trying to send to the car
		assertEquals(true, model.isSearching.value)
		assertEquals(false, model.searchFailed.value)
		context.run(model.searchStatus.value!!)
		verify(context).getString(R.string.lbl_navigation_listener_pending)
		clearInvocations(context)
		verify(navigationTrigger).triggerNavigation("rhmiCommandString")
		clearInvocations(navigationTrigger)

		// the car missed it the first time, try to send again
		advanceTimeBy(NavigationSearchController.TIMEOUT + 100)
		verify(navigationTrigger).triggerNavigation("rhmiCommandString")
		clearInvocations(navigationTrigger)

		// the car missed it the second time, try to send again
		advanceTimeBy(NavigationSearchController.TIMEOUT + 100)
		verify(navigationTrigger).triggerNavigation("rhmiCommandString")
		clearInvocations(navigationTrigger)

		// the car missed it the third time, should not try again
		advanceTimeBy(NavigationSearchController.TIMEOUT + 100)
		verify(navigationTrigger, never()).triggerNavigation("rhmiCommandString")

		// UI should update with success
		assertEquals(false, model.isSearching.value)
		context.run(model.searchStatus.value!!)
		verify(context).getString(R.string.lbl_navigation_listener_unsuccess)
		clearInvocations(context)

		// hide the text after a timeout
		advanceTimeBy(NavigationSearchController.SUCCESS)
		assertEquals("", context.run(model.searchStatus.value!!))
		verify(context, never()).getString(any())
	}
}