package me.hufman.androidautoidrive

import android.location.Address
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import me.hufman.androidautoidrive.carapp.navigation.NavigationTriggerApp
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIAction
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIApplicationConcrete
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIEvent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIModel
import org.junit.Assert.*
import org.junit.Test

class NavTriggerTest {
	@Test
	fun testNavTrigger() {
		val app = RHMIApplicationConcrete()
		val navModel = RHMIModel.RaDataModel(app, 550)
		val navAction = RHMIAction.LinkAction(app, 563)
		navAction.linkModel = navModel.id
		navAction.actionType = "navigate"
		val navEvent = RHMIEvent.ActionEvent(app, 3)
		navEvent.action = navAction.id
		app.models[navModel.id] = navModel
		app.actions[navAction.id] = navAction
		app.events[navEvent.id] = navEvent

		val destination = mock<Address> {
			on {latitude} doReturn 1.0
			on {longitude} doReturn  2.0
			on {featureName} doReturn "Test Place"
		}

		val trigger = NavigationTriggerApp(app)
		trigger.triggerNavigation(destination)
		assertEquals(";;;;;;;11930464.705555556;23860929.411111113;Test Place", navModel.value)
		assertTrue(app.triggeredEvents.containsKey(navEvent.id))
	}
}