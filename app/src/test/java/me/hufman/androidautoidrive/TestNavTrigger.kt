package me.hufman.androidautoidrive

import me.hufman.androidautoidrive.carapp.NavigationTrigger.Companion.parseUrl
import me.hufman.androidautoidrive.carapp.NavigationTriggerApp
import me.hufman.idriveconnectionkit.rhmi.RHMIAction
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationConcrete
import me.hufman.idriveconnectionkit.rhmi.RHMIEvent
import me.hufman.idriveconnectionkit.rhmi.RHMIModel
import org.junit.Assert.*
import org.junit.Test

class TestNavTrigger {
	@Test
	fun testParseGeo() {
		val invalid = "geo:abc"
		val incorrect = parseUrl(invalid)
		assertNull(incorrect)

		val geo = "geo:37.786971,-122.399677;u=35"
		val parsed = parseUrl(geo)
		assertEquals(";;;;;;;450816123.84535134;-1460285026.4199002;", parsed)

		val geoQuery = "geo:0,0?q=37.330593,-121.859425"
		val parsedQuery = parseUrl(geoQuery)
		assertEquals(";;;;;;;445371322.2239593;-1453839569.0017943;", parsedQuery)
	}

	@Test
	fun testParsePlusCode() {
		val parsed = parseUrl("http://plus.codes/849VQJQ5+XX")
		assertEquals(";;;;;;;450851515.5689004;-1460170320.9669886;", parsed)

		val cantParseShort = parseUrl("https://plus.codes/QJQ5+XX,San%20Francisco")
		assertNull(cantParseShort)
	}

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

		val trigger = NavigationTriggerApp(app)
		trigger.triggerNavigation("Test")
		assertEquals("Test", navModel.value)
		assertTrue(app.triggeredEvents.containsKey(navEvent.id))
	}
}