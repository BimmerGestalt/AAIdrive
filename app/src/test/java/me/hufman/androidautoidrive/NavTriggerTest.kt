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

class NavTriggerTest {
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

		val geo = "geo:37.786971,+-122.399677+;u=35"
		val parsed = parser.parseUrl(geo)
		assertEquals(";;;;;;;450816123.84535134;-1460285026.4199002;", parsed)

		val geoQuery = "geo:0,0?q=37.330593+,-121.859425"
		val parsedQuery = parser.parseUrl(geoQuery)
		assertEquals(";;;;;;;445371322.2239593;-1453839569.0017943;", parsedQuery)

		val geoLabel = "geo:0,0?q=37.330593,-121.859425%28Main+Street%29"
		val parsedLabel = parser.parseUrl(geoLabel)
		assertEquals(";;;;;;;445371322.2239593;-1453839569.0017943;Main Street", parsedLabel)

		val geoSpaceLabel = "geo:0,0?q=37.330593,-121.859425+%28Main+Street%29"
		val parsedSpaceLabel = parser.parseUrl(geoSpaceLabel)
		assertEquals(";;;;;;;445371322.2239593;-1453839569.0017943;Main Street", parsedSpaceLabel)

		val geoWhiteSpaceLabel = "geo:0,0?q=37.330593,-121.859425 (Main Street)"
		val parsedWhiteSpaceLabel = parser.parseUrl(geoWhiteSpaceLabel)
		assertEquals(";;;;;;;445371322.2239593;-1453839569.0017943;Main Street", parsedWhiteSpaceLabel)

		// free form query
		val addressResult = mock<Address> {
			on { latitude } doReturn 37.7773
			on { longitude } doReturn -122.41
			on { thoroughfare } doReturn "1970 Naglee Ave"
			on { locality } doReturn "San Jose, CA"
			on { postalCode } doReturn "95126"
			on { countryCode } doReturn "US"
		}
		whenever(addressSearcher.search(any())) doReturn addressResult
		val correctAnswer = ";;Naglee Ave;1970;95126;San Jose, CA;US;450700744.3211838;-1460408184.6070554;"

		val geoAddress = "geo:0,0?q=1970+Naglee+Ave+San+Jose,+CA+95126"
		val addressParsed = parser.parseUrl(geoAddress)
		assertEquals(correctAnswer, addressParsed)
		val geoWhitespaceAddress = "geo:0,0?q=1970 Naglee Ave San Jose, CA 95126"
		val addressWhitespaceParsed = parser.parseUrl(geoWhitespaceAddress)
		assertEquals(correctAnswer, addressWhitespaceParsed)
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

		val googleOlc = parser.parseUrl("https://www.google.com/maps/dir//849VQJQ5+XX")
		assertEquals(";;;;;;;450851515.5689004;-1460170320.9669886;", googleOlc)

		val addressResult = mock<Address> {
			on { latitude } doReturn 37.7773
			on { longitude } doReturn -122.41
		}
		whenever(addressSearcher.search(eq("San Francisco"))) doReturn addressResult
		val canParseShort = parser.parseUrl("https://plus.codes/QJQ5+XX,San%20Francisco")
		assertEquals(";;;;;;;450851515.5689004;-1460170320.9669886;", canParseShort)

		val googleShortOlc = parser.parseUrl("https://www.google.com/maps/dir//QJQ5+XX%20San%20Francisco")
		assertEquals(";;;;;;;450851515.5689004;-1460170320.9669886;", googleShortOlc)
	}

	@Test
	fun testParseGoogle() {
		val zanotto = "http://maps.google.com/maps?q=1970+Naglee+Ave+San+Jose,+CA+95126;&ie=UTF8&hl=en&hq=&ll=37.335378,-121.931098&spn=0,359.967062"
		assertEquals(";;;;;;;445428409.4975754;-1454694661.1986356;1970 Naglee Ave San Jose, CA 95126 ", parser.parseUrl(zanotto))

		val officialExample = "https://www.google.com/maps/search/?api=1&query=47.5951518+,-122.3316393"
		assertEquals(";;;;;;;567832278.7054589;-1459473305.041403;", parser.parseUrl(officialExample))

		val officialDirExample = "https://www.google.com/maps/dir/?api=1&destination=47.5951518,+-122.3316393"
		assertEquals(";;;;;;;567832278.7054589;-1459473305.041403;", parser.parseUrl(officialDirExample))

		val dirPath = "https://www.google.com/maps/dir/Current+Location/47.5951518+,-122.3316393"
		assertEquals(";;;;;;;567832278.7054589;-1459473305.041403;", parser.parseUrl(dirPath))

		val intentUrl = "google.navigation:q=47.5951518,-122.3316393"
		assertEquals(";;;;;;;567832278.7054589;-1459473305.041403;",  parser.parseUrl(intentUrl))

		// more formats from https://stackoverflow.com/questions/2660201/
		val pathPlace = "http://maps.google.com/maps/place/%3Cname%3E/@47.5951518+,-122.3316393,15z"
		assertEquals(";;;;;;;567832278.7054589;-1459473305.041403;<name>", parser.parseUrl(pathPlace))
		val searchPath = "https://www.google.com/maps/search/47.5951518,-122.3316393"
		assertEquals(";;;;;;;567832278.7054589;-1459473305.041403;", parser.parseUrl(searchPath))
		val locQuery = "http://maps.google.co.uk/maps?q=loc:+47.5951518,-122.3316393&z=15"
		assertEquals(";;;;;;;567832278.7054589;-1459473305.041403;", parser.parseUrl(locQuery))
		val zanottoQuery = "http://maps.google.com/?q=1970+Naglee+Ave+San+Jose,+CA+95126;&ie=UTF8&hl=en&hq=&ll=37.335378,-121.931098&spn=0,359.967062"
		assertEquals(";;;;;;;445428409.4975754;-1454694661.1986356;1970 Naglee Ave San Jose, CA 95126 ", parser.parseUrl(zanottoQuery))
		val llQuery = "https://maps.google.de/maps?q=47.5951518,-122.3316393&z=17&t=k"
		assertEquals(";;;;;;;567832278.7054589;-1459473305.041403;",  parser.parseUrl(llQuery))
	}

	@Test
	fun testParseGoogleQueries() {
		// without a valid address searcher
		val emptyAddress = "http://maps.google.com/maps?q=1970+Naglee+Ave+San+Jose,+CA+95126&ie=UTF8"
		assertNull(parser.parseUrl(emptyAddress))
		val emptyUrl = "google.navigation:q=1970+Naglee+Ave+San+Jose,+CA+95126"
		assertNull(parser.parseUrl(emptyUrl))

		val addressResult = mock<Address> {
			on { latitude } doReturn 37.7773
			on { longitude } doReturn -122.41
			on { thoroughfare } doReturn "1970 Naglee Ave"
			on { locality } doReturn "San Jose, CA"
			on { postalCode } doReturn "95126"
			on { countryCode } doReturn "US"
		}
		whenever(addressSearcher.search(eq("1970 Naglee Ave San Jose, CA 95126"))) doReturn addressResult
		val correctAnswer = ";;Naglee Ave;1970;95126;San Jose, CA;US;450700744.3211838;-1460408184.6070554;"

		val zanotto = "http://maps.google.com/maps?q=1970+Naglee+Ave+San+Jose,+CA+95126&ie=UTF8"
		assertEquals(correctAnswer, parser.parseUrl(zanotto))

		val zanottoQuery = "http://maps.google.com/?q=1970+Naglee+Ave+San+Jose,+CA+95126"
		assertEquals(correctAnswer, parser.parseUrl(zanottoQuery))

		val officialExample = "https://www.google.com/maps/search/?api=1&query=1970+Naglee+Ave+San+Jose,+CA+95126&query_place_id=unknown"
		assertEquals(correctAnswer, parser.parseUrl(officialExample))

		val officialDirExample = "https://www.google.com/maps/dir/?api=1&destination=1970+Naglee+Ave+San+Jose,+CA+95126&travelmode=driving"
		assertEquals(correctAnswer, parser.parseUrl(officialDirExample))

		val intentUrl = "google.navigation:q=1970+Naglee+Ave+San+Jose,+CA+95126&mode=d"
		assertEquals(correctAnswer, parser.parseUrl(intentUrl))

		val daddr = "http://maps.google.com/maps?saddr=1970+Naglee+Ave+San+Jose,+CA+95126&daddr=1970%20Naglee%20Ave%20San%20Jose,%20CA%2095126"
		assertEquals(correctAnswer, parser.parseUrl(daddr))

		val pathDirQuery = "https://www.google.com/maps/dir//1970%20Naglee%20Ave%20San%20Jose,%20CA%2095126"
		assertEquals(correctAnswer, parser.parseUrl(pathDirQuery))

		val pathSearchQuery = "https://www.google.com/maps/search/1970%20Naglee%20Ave%20San%20Jose,%20CA%2095126"
		assertEquals(correctAnswer, parser.parseUrl(pathSearchQuery))
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