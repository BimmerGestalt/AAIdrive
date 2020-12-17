package me.hufman.androidautoidrive.carapp.navigation

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import com.google.openlocationcode.OpenLocationCode
import me.hufman.androidautoidrive.carapp.maps.LatLong
import java.lang.IllegalArgumentException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder


interface AddressSearcher {
	fun search(query: String): Address?
}

class AndroidGeocoderSearcher(context: Context): AddressSearcher {
	val geocoder = Geocoder(context)
	override fun search(query: String): Address? {
		return geocoder.getFromLocationName(query, 1)?.getOrNull(0)
	}
}

class NavigationParser(val addressSearcher: AddressSearcher) {
	companion object {
		val TAG = "NavigationParser"
		val NUM_MATCHER = Regex("^([0-9]+)\\s+(.*)")
		val LATLNG_MATCHER = Regex("^([-0-9.]+\\s*,\\s*[-0-9.]+).*")
		val LATLNG_LABEL_MATCHER = Regex("^q=([-0-9.]+\\s*,\\s*[-0-9.]+\\s*)(,[-0-9.]+)?\\s*(\\((.*)\\))?$")
		val PLUSCODE_SPLITTER = Regex("^([2-9CFGHJMPQRVWX+]+)([, ](.*))?")
		val PLUSCODE_URL_MATCHER = Regex("^/+([2-9CFGHJMPQRVWX+]+([, ](.*))?)$")
		val GOOGLE_OLC_MATCHER = Regex("^/maps/dir/+([2-9CFGHJMPQRVWX+]+([, ](.*))?)")
		val GOOGLE_Q_MATCHER = Regex("^(.*[&?])?q=([^&]*).*")
		val GOOGLE_QLL_MATCHER = Regex("^(.*[&?])?q=([-0-9.]+,\\s*[-0-9.]+).*")
		val GOOGLE_LL_MATCHER = Regex("^(.*[&?])?ll=([-0-9.]+,\\s*[-0-9.]+).*")
		val GOOGLE_SEARCHPATHLL_MATCHER = Regex("/maps/search/+()([-0-9.]+\\s*,\\s*[-0-9.]+\\s*).*")
		val GOOGLE_PLACEPATHLL_MATCHER = Regex("/maps/place/([^/]*)/+@([-0-9.]+\\s*,\\s*[-0-9.]+\\s*).*")
		val GOOGLE_DIRPATHLL_MATCHER = Regex("/maps/dir/[^/]*/+()([-0-9.]+\\s*,\\s*[-0-9.]+\\s*).*")
		val GOOGLE_SEARCHPATH_MATCHER = Regex("/maps/search/+([^/]+).*")
		val GOOGLE_DIRPATH_MATCHER = Regex("/maps/dir/[^/]*/+([^/]+).*")
		val GOOGLE_QUERYLL_MATCHER = Regex("^(.*[&?])?(q|query|daddr|destination)=(loc:\\s+)?([-0-9.]+\\s*,\\s*[-0-9.]+\\s*).*")
		val GOOGLE_QUERY_MATCHER = Regex("^(.*[&?])?(q|query|daddr|destination)=([^&]*).*")

		fun latlongToRHMI(latlong: String, label: String = ""): String {
			val splits = latlong.split(',')
			return latlongToRHMI(LatLong(splits[0].toDouble(), splits[1].toDouble()), label)
		}

		fun latlongToRHMI(latlong: LatLong, label: String = ""): String {
			// [lastName];[firstName];[street];[houseNumber];[zipCode];[city];[country];[latitude];[longitude];[poiName]
			val lat = (latlong.latitude / 360 * Int.MAX_VALUE * 2).toBigDecimal()
			val lng = (latlong.longitude / 360 * Int.MAX_VALUE * 2).toBigDecimal()
			return ";;;;;;;$lat;$lng;${label.replace(';', ' ')}"
		}

		fun addressToRHMI(address: Address): String {
			// [lastName];[firstName];[street];[houseNumber];[zipCode];[city];[country];[latitude];[longitude];[poiName]
			val houseAddress = NUM_MATCHER.matchEntire(address.thoroughfare ?: "")
			val houseNumber = houseAddress?.groupValues?.getOrNull(1) ?: ""
			val street = houseAddress?.groupValues?.getOrNull(2) ?: ""
			val lat = (address.latitude / 360 * Int.MAX_VALUE * 2).toBigDecimal()
			val lng = (address.longitude / 360 * Int.MAX_VALUE * 2).toBigDecimal()
			val label = address.featureName?.replace(';', ' ') ?: ""
			return ";;$street;$houseNumber;${address.postalCode ?: ""};${address.locality ?: ""};${address.countryCode ?: ""};$lat;$lng;$label"
		}

		fun parseUri(url: String): URI {
			try {
				return URI(url)
			} catch (e: URISyntaxException) {
				return URI(URLEncoder.encode(url, "UTF-8"))
			}
		}
	}

	fun parseUrl(url: String?): String? {
		url ?: return null
		if (url.startsWith("geo:")) return parseGeoUrl(url)
		if (url.startsWith("google.navigation:")) return parseGoogleUri(url)
		if (url.startsWith("http")) {
			return parsePlusUrl(url) ?:
				parseGoogleUrl(url)
		}
		return null
	}

	private fun parseGeoUrl(url: String): String? {
		val uri = parseUri(url)
		val data = uri.schemeSpecificPart.replace('+', ' ')
		val query = if (data.contains('?')) { data.split('?', limit=2)[1] } else null

		val authorityResult = LATLNG_MATCHER.matchEntire(data)
		val queryResult = LATLNG_LABEL_MATCHER.matchEntire(query ?: "")

		// find a text query
		// geo:0,0?q=1600 Amphitheatre Parkway, Mountain+View, California
		if (query?.startsWith("q=") == true && queryResult == null) {
			val search = query.substring(2)
			val result = addressSearcher.search(search)
			if (result != null) {
				Log.i(TAG, "Parsed $search to $result -> ${addressToRHMI(result)}")
				return addressToRHMI(result)
			}
		}

		// latlng in the geo url
		val latlng = queryResult?.groupValues?.getOrNull(1) ?: authorityResult?.groupValues?.getOrNull(1)
		val latlong = if (latlng?.contains(',') == true) {
			val splits = latlng.split(',')
			LatLong(splits[0].trim().toDouble(), splits[1].trim().toDouble())
		} else null
		val label = queryResult?.groupValues?.getOrNull(4) ?: ""
		if (latlong != null) return latlongToRHMI(latlong, label)   // found a latlong

		return null
	}

	private fun parsePlusUrl(url: String): String? {
		if (!url.contains("plus.codes")) return null
		val uri = parseUri(url)
		val matcher = PLUSCODE_URL_MATCHER.matchEntire(uri.path) ?: return null
		return parsePlusCode(matcher.groupValues[1])
	}

	private fun parsePlusCode(plusCode: String): String? {
		val parsed = PLUSCODE_SPLITTER.matchEntire(plusCode) ?: return null
		val code = parsed.groupValues[1]
		val reference = parsed.groupValues[3]
		var olc = try {
			OpenLocationCode(code)
		} catch (e: IllegalArgumentException) {
			return null
		}
		if (olc.isShort && reference.isNotBlank()) {
			val referenceName = reference.replace('+', ' ').trim()
			val result = addressSearcher.search(referenceName) ?: return null
			olc = olc.recover(result.latitude, result.longitude)
		} else if (olc.isShort) {
			// can't resolve short code
			return null
		}

		val area = olc.decode()
		return latlongToRHMI(LatLong(area.centerLatitude, area.centerLongitude))
	}

	private fun parseGoogleUri(url: String): String? {
		// https://developers.google.com/maps/documentation/urls/android-intents
		// google.navigation:q=Taronga+Zoo,+Sydney+Australia
		val uri = parseUri(url)
		val data = uri.schemeSpecificPart.replace('+', ' ')
		val googleQLL = GOOGLE_QLL_MATCHER.matchEntire(data)
		if (googleQLL != null) {
			return latlongToRHMI(googleQLL.groupValues[2])
		}

		val googleQ = GOOGLE_Q_MATCHER.matchEntire(data)
		if (googleQ != null) {
			val q = googleQ.groupValues[2]
			val result = addressSearcher.search(q)
			if (result != null) {
				Log.i(TAG, "Parsed $q to $result -> ${addressToRHMI(result)}")
				return addressToRHMI(result)
			}
		}

		return null
	}

	private fun parseGoogleUrl(url: String): String? {
		val uri = parseUri(url)
		if (!uri.authority.contains("google")) return null

		val path = uri.path?.replace('+', ' ') ?: ""
		// https://www.google.com/maps/dir/Current+Location/47.5951518,-122.3316393
		// http://maps.google.com/maps/place/<name>/@47.5951518,-122.3316393,15z
		// https://www.google.com/maps/search/47.5951518,-122.3316393
		val googlePathLL = GOOGLE_DIRPATHLL_MATCHER.matchEntire(path) ?: GOOGLE_PLACEPATHLL_MATCHER.matchEntire(path) ?: GOOGLE_SEARCHPATHLL_MATCHER.matchEntire(path)
		if (googlePathLL != null) {
			return latlongToRHMI(googlePathLL.groupValues[2], googlePathLL.groupValues[1])
		}
		// https://www.google.com/maps/dir//QJQ5+XX,San%20Francisco
		val googleOlc = GOOGLE_OLC_MATCHER.matchEntire(uri.path ?: "")
		if (googleOlc != null) {
			return parsePlusCode(googleOlc.groupValues[1])
		}

		// https://www.google.com/maps/search/1970+Naglee+Ave+San+Jose,+CA
		// https://www.google.com/maps/dir/Current+Location/1970+Naglee+Ave+San+Jose,+CA
		val googlePath = GOOGLE_DIRPATH_MATCHER.matchEntire(path) ?: GOOGLE_SEARCHPATH_MATCHER.matchEntire(path)
		if (googlePath != null) {
			val result = addressSearcher.search(googlePath.groupValues[1])
			if (result != null) {
				Log.i(TAG, "Parsed ${googlePath.groupValues[1]} to $result -> ${addressToRHMI(result)}")
				return addressToRHMI(result)
			}
		}

		val query = uri.query?.replace('+', ' ') ?: ""

		// https://developers.google.com/maps/documentation/urls/get-started
		// https://www.google.com/maps/search/?api=1&query=47.5951518,-122.3316393
		// https://www.google.com/maps/dir/?api=1&destination=47.5951518,-122.3316393
		val googleQueryLL = GOOGLE_QUERYLL_MATCHER.matchEntire(query)
		val googleQuery = GOOGLE_QUERY_MATCHER.matchEntire(query)
		if (googleQueryLL != null) {
			return latlongToRHMI(googleQueryLL.groupValues[4], "")
		}

		// some urls can include an &ll parameter, use that instead of conducting a search
		// http://maps.google.com/maps?q=1970+Naglee+Ave+San+Jose,+CA+95126&ie=UTF8&hl=en&hq=&hnear=1970+Naglee+Ave,+San+Jose,+Santa+Clara,+California+95126&ll=37.335378,-121.931098&spn=0,359.967062&z=16&layer=c&cbll=37.328304,-121.931342&panoid=kfyFC9pOgbTvYFIkHYnsMQ&cbp=12,193.03,,0,2.36
		// http://maps.google.com/maps?q=loc:47.5951518,-122.3316393&z=15
		val googleLL = GOOGLE_LL_MATCHER.matchEntire(query)
		if (googleLL != null) {
			return latlongToRHMI(googleLL.groupValues[2], googleQuery?.groupValues?.getOrNull(3) ?: "")
		}

		// https://developers.google.com/maps/documentation/urls/get-started
		// https://www.google.com/maps/search/?api=1&query=pizza+seattle+wa
		// https://www.google.com/maps/dir/?api=1&destination=pizza+seattle+wa
		if (googleQuery != null) {
			val result = addressSearcher.search(googleQuery.groupValues[3])
			if (result != null) {
				Log.i(TAG, "Parsed ${googleQuery.groupValues[3]} to $result -> ${addressToRHMI(result)}")
				return addressToRHMI(result)
			}
		}

		return null
	}
}