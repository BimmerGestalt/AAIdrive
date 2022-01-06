package me.hufman.androidautoidrive

import android.location.Address
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.carapp.navigation.AddressSearcher
import me.hufman.androidautoidrive.carapp.navigation.NavigationParser
import me.hufman.androidautoidrive.carapp.navigation.URLRedirector
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class NavigationParserTest {
	lateinit var addressSearcher: AddressSearcher
	var redirector = mock<URLRedirector>()
	lateinit var parser: NavigationParser

	@Before
	fun setUp() {
		addressSearcher = mock()
		parser = NavigationParser(addressSearcher, redirector)
	}

	@Test
	fun testParseUrl() {
		Assert.assertNull(parser.parseUrl("smtp:lol"))
	}

	@Test
	fun testParseGeo() {
		val invalid = "geo:abc"
		val incorrect = parser.parseUrl(invalid)
		Assert.assertNull(incorrect)

		val mainstreetAddressResult = Address(Locale.ROOT).apply {
			latitude = 37.330593
			longitude = -121.859425
			featureName = ""
		}
		val mainstreetNamedAddressResult = Address(Locale.ROOT).apply {
			latitude = 37.330593
			longitude = -121.859425
			featureName = "Main Street"
		}
		val geo = "geo:37.330593,+-121.859425+;u=35"
		val parsed = parser.parseUrl(geo)
		Assert.assertEquals(mainstreetAddressResult.toString(), parsed.toString())

		val geoQuery = "geo:0,0?q=37.330593+,-121.859425"
		val parsedQuery = parser.parseUrl(geoQuery)
		Assert.assertEquals(mainstreetAddressResult.toString(), parsedQuery.toString())

		val geoLabel = "geo:0,0?q=37.330593,-121.859425%28Main+Street%29"
		val parsedLabel = parser.parseUrl(geoLabel)
		Assert.assertEquals(mainstreetNamedAddressResult.toString(), parsedLabel.toString())

		val geoSpaceLabel = "geo:0,0?q=37.330593,-121.859425+%28Main+Street%29"
		val parsedSpaceLabel = parser.parseUrl(geoSpaceLabel)
		Assert.assertEquals(mainstreetNamedAddressResult.toString(), parsedSpaceLabel.toString())

		val geoWhiteSpaceLabel = "geo:0,0?q=37.330593,-121.859425 (Main Street)"
		val parsedWhiteSpaceLabel = parser.parseUrl(geoWhiteSpaceLabel)
		Assert.assertEquals(mainstreetNamedAddressResult.toString(), parsedWhiteSpaceLabel.toString())

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

		val geoAddress = "geo:0,0?q=1970+Naglee+Ave+San+Jose,+CA+95126"
		val addressParsed = parser.parseUrl(geoAddress)
		Assert.assertEquals(addressResult.toString(), addressParsed.toString())
		val geoWhitespaceAddress = "geo:0,0?q=1970 Naglee Ave San Jose, CA 95126"
		val addressWhitespaceParsed = parser.parseUrl(geoWhitespaceAddress)
		Assert.assertEquals(addressResult.toString(), addressWhitespaceParsed.toString())

		// unknown address
		whenever(addressSearcher.search(any())) doReturn null
		val empty = parser.parseUrl("geo:0,0?q=")
		Assert.assertNull(empty)
		val unknown = parser.parseUrl("geo:0,0?q=missingLocation")
		Assert.assertNull(unknown)

	}

	@Test
	fun testParsePlusCode() {
		val addressResult = Address(Locale.ROOT).apply {
			latitude = 37.7899375
			longitude = -122.3900625
			featureName = ""
		}

		val invalid = parser.parseUrl("https://plus.codes/849QJQ5+XX")
		Assert.assertNull(invalid)

		val parsed = parser.parseUrl("http://plus.codes/849VQJQ5+XX")
		Assert.assertEquals(addressResult.toString(), parsed.toString())

		val invalidShort = parser.parseUrl("https://plus.codes/QJQ5+XX")
		Assert.assertNull(invalidShort)
		val cantParseShort = parser.parseUrl("https://plus.codes/QJQ5+XX,San%20Francisco")
		Assert.assertNull(cantParseShort)

		val googleOlc = parser.parseUrl("https://www.google.com/maps/dir//849VQJQ5+XX")
		Assert.assertEquals(addressResult.toString(), googleOlc.toString())

		whenever(addressSearcher.search(eq("San Francisco"))) doReturn addressResult
		val canParseShort = parser.parseUrl("https://plus.codes/QJQ5+XX,San%20Francisco")
		Assert.assertEquals(addressResult.toString(), canParseShort.toString())

		val googleShortOlc = parser.parseUrl("https://www.google.com/maps/dir//QJQ5+XX%20San%20Francisco")
		Assert.assertEquals(addressResult.toString(), googleShortOlc.toString())
	}

	@Test
	fun testParseGoogle() {
		val correctAnswer = Address(Locale.ROOT).apply {
			latitude = 47.5951518
			longitude = -122.3316393
			featureName = ""
		}
		val namedAnswer = Address(Locale.ROOT).apply {
			latitude = 47.5951518
			longitude = -122.3316393
			featureName = "<name>"
		}

		val zanotto = "http://maps.google.com/maps?q=%3Cname%3E&ie=UTF8&hl=en&hq=&ll=47.5951518,-122.3316393&spn=0,359.967062"
		Assert.assertEquals(namedAnswer.toString(), parser.parseUrl(zanotto).toString())

		val officialExample = "https://www.google.com/maps/search/?api=1&query=47.5951518+,-122.3316393"
		Assert.assertEquals(correctAnswer.toString(), parser.parseUrl(officialExample).toString())

		val officialDirExample = "https://www.google.com/maps/dir/?api=1&destination=47.5951518,+-122.3316393"
		Assert.assertEquals(correctAnswer.toString(), parser.parseUrl(officialDirExample).toString())

		val dirPath = "https://www.google.com/maps/dir/Current+Location/47.5951518+,-122.3316393"
		Assert.assertEquals(correctAnswer.toString(), parser.parseUrl(dirPath).toString())

		val intentUrl = "google.navigation:q=47.5951518,-122.3316393"
		Assert.assertEquals(correctAnswer.toString(), parser.parseUrl(intentUrl).toString())

		// more formats from https://stackoverflow.com/questions/2660201/
		val pathPlace = "http://maps.google.com/maps/place/%3Cname%3E/@47.5951518+,-122.3316393,15z"
		Assert.assertEquals(namedAnswer.toString(), parser.parseUrl(pathPlace).toString())
		val searchPath = "https://www.google.com/maps/search/47.5951518,-122.3316393"
		Assert.assertEquals(correctAnswer.toString(), parser.parseUrl(searchPath).toString())
		val locQuery = "http://maps.google.co.uk/maps?q=loc:+47.5951518,-122.3316393&z=15"
		Assert.assertEquals(correctAnswer.toString(), parser.parseUrl(locQuery).toString())
		val zanottoQuery = "http://maps.google.com/?q=%3Cname%3E&ie=UTF8&hl=en&hq=&ll=47.5951518,-122.3316393&spn=0,359.967062"
		Assert.assertEquals(namedAnswer.toString(), parser.parseUrl(zanottoQuery).toString())
		val llQuery = "https://maps.google.de/maps?q=47.5951518,-122.3316393&z=17&t=k"
		Assert.assertEquals(correctAnswer.toString(), parser.parseUrl(llQuery).toString())
	}

	@Test
	fun testParseGoogleQueries() {
		// without a valid address searcher
		val emptyAddress = "http://maps.google.com/maps?q=1970+Naglee+Ave+San+Jose,+CA+95126&ie=UTF8"
		Assert.assertNull(parser.parseUrl(emptyAddress))
		val emptyUrl = "google.navigation:q=1970+Naglee+Ave+San+Jose,+CA+95126"
		Assert.assertNull(parser.parseUrl(emptyUrl))

		val addressResult = mock<Address> {
			on { latitude } doReturn 37.7773
			on { longitude } doReturn -122.41
			on { thoroughfare } doReturn "1970 Naglee Ave"
			on { locality } doReturn "San Jose, CA"
			on { postalCode } doReturn "95126"
			on { countryCode } doReturn "US"
		}
		whenever(addressSearcher.search(eq("1970 Naglee Ave San Jose, CA 95126"))) doReturn addressResult

		val zanotto = "http://maps.google.com/maps?q=1970+Naglee+Ave+San+Jose,+CA+95126&ie=UTF8"
		Assert.assertEquals(addressResult, parser.parseUrl(zanotto))

		val zanottoQuery = "http://maps.google.com/?q=1970+Naglee+Ave+San+Jose,+CA+95126"
		Assert.assertEquals(addressResult, parser.parseUrl(zanottoQuery))

		val officialExample = "https://www.google.com/maps/search/?api=1&query=1970+Naglee+Ave+San+Jose,+CA+95126&query_place_id=unknown"
		Assert.assertEquals(addressResult, parser.parseUrl(officialExample))

		val officialDirExample = "https://www.google.com/maps/dir/?api=1&destination=1970+Naglee+Ave+San+Jose,+CA+95126&travelmode=driving"
		Assert.assertEquals(addressResult, parser.parseUrl(officialDirExample))

		val intentUrl = "google.navigation:q=1970+Naglee+Ave+San+Jose,+CA+95126&mode=d"
		Assert.assertEquals(addressResult, parser.parseUrl(intentUrl))

		val daddr = "http://maps.google.com/maps?saddr=1970+Naglee+Ave+San+Jose,+CA+95126&daddr=1970%20Naglee%20Ave%20San%20Jose,%20CA%2095126"
		Assert.assertEquals(addressResult, parser.parseUrl(daddr))

		val pathDirQuery = "https://www.google.com/maps/dir//1970%20Naglee%20Ave%20San%20Jose,%20CA%2095126"
		Assert.assertEquals(addressResult, parser.parseUrl(pathDirQuery))

		val pathPlaceQuery = "https://www.google.com/maps/place/1970%20Naglee%20Ave%20San%20Jose,%20CA%2095126"
		Assert.assertEquals(addressResult, parser.parseUrl(pathPlaceQuery))

		val pathSearchQuery = "https://www.google.com/maps/search/1970%20Naglee%20Ave%20San%20Jose,%20CA%2095126"
		Assert.assertEquals(addressResult, parser.parseUrl(pathSearchQuery))
	}

	@Test
	fun testGoogleRedirect() {
		val correctAnswer = Address(Locale.ROOT).apply {
			latitude = 47.5951518
			longitude = -122.3316393
			featureName = ""
		}

		// handle a maps.app.goo.gl redirect
		whenever(redirector.tryRedirect("https://maps.app.goo.gl/test")) doReturn "https://maps.google.de/maps?q=47.5951518,-122.3316393&z=17&t=k"
		whenever(redirector.tryRedirect("https://goo.gl/maps/test")) doReturn "https://maps.google.de/maps?q=47.5951518,-122.3316393&z=17&t=k"
		val mapsShortener = "https://maps.app.goo.gl/test"
		Assert.assertEquals(correctAnswer.toString(), parser.parseUrl(mapsShortener).toString())
		val googShortener = "https://goo.gl/maps/test"
		Assert.assertEquals(correctAnswer.toString(), parser.parseUrl(googShortener).toString())

		// redirects to an unknown location
		val otherwise = "https://somewhere.com/test"
		Assert.assertEquals(null, parser.parseUrl(otherwise))
	}
}