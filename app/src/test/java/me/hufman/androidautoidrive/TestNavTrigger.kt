package me.hufman.androidautoidrive

import android.location.Address
import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.carapp.navigation.AddressSearcher
import me.hufman.androidautoidrive.carapp.navigation.NavigationParser
import me.hufman.androidautoidrive.carapp.navigation.NavigationTriggerApp
import me.hufman.idriveconnectionkit.rhmi.RHMIAction
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationConcrete
import me.hufman.idriveconnectionkit.rhmi.RHMIEvent
import me.hufman.idriveconnectionkit.rhmi.RHMIModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TestNavTrigger {
	lateinit var addressSearcher: AddressSearcher
	lateinit var parser: NavigationParser

	@Before
	fun setUp() {
		addressSearcher = mock()
		parser = NavigationParser(addressSearcher)
	}

	@Test
	fun testParseUrl() {
		assertNull(parser.parseUrl("smtp:lol"))
	}

	@Test
	fun testParseGeo() {
		val invalid = "geo:abc"
		val incorrect = parser.parseUrl(invalid)
		assertNull(incorrect)

		val geo = "geo:37.786971,-122.399677;u=35"
		val parsed = parser.parseUrl(geo)
		assertEquals(";;;;;;;450816123.84535134;-1460285026.4199002;", parsed)

		val geoQuery = "geo:0,0?q=37.330593,-121.859425"
		val parsedQuery = parser.parseUrl(geoQuery)
		assertEquals(";;;;;;;445371322.2239593;-1453839569.0017943;", parsedQuery)

		val geoLabel = "geo:0,0?q=37.330593,-121.859425(Main Street)"
		val parsedLabel = parser.parseUrl(geoLabel)
		assertEquals(";;;;;;;445371322.2239593;-1453839569.0017943;Main Street", parsedLabel)

		val geoSpaceLabel = "geo:0,0?q=37.330593,-121.859425 (Main Street)"
		val parsedSpaceLabel = parser.parseUrl(geoSpaceLabel)
		assertEquals(";;;;;;;445371322.2239593;-1453839569.0017943;Main Street", parsedSpaceLabel)
	}

	@Test
	fun testParsePlusCode() {
		val invalid = parser.parseUrl("https://plus.codes/849QJQ5+XX")
		assertNull(invalid)

		val parsed = parser.parseUrl("http://plus.codes/849VQJQ5+XX")
		assertEquals(";;;;;;;450851515.5689004;-1460170320.9669886;", parsed)

		val invalidShort = parser.parseUrl("https://plus.codes/QJQ5+XX")
		assertNull(invalidShort)
		val cantParseShort = parser.parseUrl("https://plus.codes/QJQ5+XX,San%20Francisco")
		assertNull(cantParseShort)

		val addressResult = mock<Address> {
			on { latitude } doReturn 37.7773
			on { longitude } doReturn -122.41
		}
		whenever(addressSearcher.search(eq("San Francisco"))) doReturn addressResult
		val canParseShort = parser.parseUrl("https://plus.codes/QJQ5+XX,San%20Francisco")
		assertEquals(";;;;;;;450851515.5689004;-1460170320.9669886;", canParseShort)
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