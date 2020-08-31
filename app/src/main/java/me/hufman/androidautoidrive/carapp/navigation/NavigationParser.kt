package me.hufman.androidautoidrive.carapp.navigation

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.google.openlocationcode.OpenLocationCode
import me.hufman.androidautoidrive.carapp.maps.LatLong
import java.lang.IllegalArgumentException
import java.net.URI


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
		val LATLNG_MATCHER = Regex("^([-0-9.]+,[-0-9.]+).*")
		val LATLNG_LABEL_MATCHER = Regex("^q=([-0-9.]+,[-0-9.]+)(,[-0-9.]+)?\\s*(\\((.*)\\))?$")
		val PLUSCODE_URL_MATCHER = Regex("^/([2-9CFGHJMPQRVWX+]+)([, ](.*))?$")

		fun latlongToRHMI(latlong: LatLong, label: String = ""): String {
			// [lastName];[firstName];[street];[houseNumber];[zipCode];[city];[country];[latitude];[longitude];[poiName]
			val lat = (latlong.latitude / 360 * Int.MAX_VALUE * 2).toBigDecimal()
			val lng = (latlong.longitude / 360 * Int.MAX_VALUE * 2).toBigDecimal()
			return ";;;;;;;$lat;$lng;$label"
		}
	}

	fun parseUrl(url: String?): String? {
		url ?: return null
		if (url.startsWith("geo:")) return parseGeoUrl(url)
		if (url.startsWith("http")) {
			return parsePlusCode(url)
		}
		return null
	}

	fun parseGeoUrl(url: String): String? {
		val data = url.substring(4)
		val query = if (data.contains('?')) { data.split('?', limit=2)[1] } else null

		val authority_result = LATLNG_MATCHER.matchEntire(data)
		val query_result = LATLNG_LABEL_MATCHER.matchEntire(query ?: "")

		val latlng = query_result?.groupValues?.getOrNull(1) ?: authority_result?.groupValues?.getOrNull(1)
		val latlong = if (latlng?.contains(',') == true) {
			val splits = latlng.split(',')
			LatLong(splits[0].toDouble(), splits[1].toDouble())
		} else null
		val label = query_result?.groupValues?.getOrNull(4)?.replace(";", "") ?: ""

		return if (latlong != null) {
			latlongToRHMI(latlong, label)
		} else {
			null
		}
	}

	fun parsePlusCode(url: String): String? {
		if (!url.contains("plus.codes")) return null
		val uri = URI(url)
		val matcher = PLUSCODE_URL_MATCHER.matchEntire(uri.path) ?: return null
		var olc = try {
			OpenLocationCode(matcher.groupValues[1])
		} catch (e: IllegalArgumentException) {
			return null
		}
		if (olc.isShort && (matcher.groupValues.getOrNull(3) ?: "").isNotBlank()) {
			val referenceName = matcher.groupValues[3].replace('+',' ')
			val result = addressSearcher.search(referenceName) ?: return null
			olc = olc.recover(result.latitude, result.longitude)
		} else if (olc.isShort) {
			// can't resolve short code
			return null
		}

		val area = olc.decode()
		return latlongToRHMI(LatLong(area.centerLatitude, area.centerLongitude))
	}
}