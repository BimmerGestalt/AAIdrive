package me.hufman.androidautoidrive.carapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import com.google.openlocationcode.OpenLocationCode
import me.hufman.androidautoidrive.carapp.NavigationTrigger.Companion.TAG
import me.hufman.androidautoidrive.carapp.maps.LatLong
import me.hufman.idriveconnectionkit.rhmi.RHMIApplication
import me.hufman.idriveconnectionkit.rhmi.RHMIEvent
import java.lang.IllegalArgumentException

interface NavigationTrigger {
	companion object {
		val TAG = "NavTrigger"
		val LATLNG_MATCHER = Regex("^([-0-9.]+,[-0-9.]+).*")
		val LATLNG_LABEL_MATCHER = Regex("^q=([-0-9.]+,[-0-9.]+)(,[-0-9.]+)?\\s*(\\(.*\\))?$")
		val PLUSCODE_URL_MATCHER = Regex("^https?://plus.codes/(.*)(,(.*))?$")

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
			val label = query_result?.groupValues?.getOrNull(3)?.replace(";", "") ?: ""

			return if (latlong != null) {
				latlongToRHMI(latlong, label)
			} else {
				null
			}
		}

		fun parsePlusCode(url: String): String? {
			val matcher = PLUSCODE_URL_MATCHER.matchEntire(url) ?: return null
			val olc = try {
				OpenLocationCode(matcher.groupValues[1])
			} catch (e: IllegalArgumentException) {
				return null
			}
			if (olc.isShort) return null    // don't know what reference point to recover from

			val area = olc.decode()
			return latlongToRHMI(LatLong(area.centerLatitude, area.centerLongitude), matcher.groupValues.getOrNull(3) ?: "")
		}

		fun latlongToRHMI(latlong: LatLong, label: String = ""): String {
			// [lastName];[firstName];[street];[houseNumber];[zipCode];[city];[country];[latitude];[longitude];[poiName]
			val lat = (latlong.latitude / 360 * Int.MAX_VALUE * 2).toBigDecimal()
			val lng = (latlong.longitude / 360 * Int.MAX_VALUE * 2).toBigDecimal()
			return ";;;;;;;$lat;$lng;$label"
		}
	}

	fun triggerNavigation(rhmiNav: String)
}

class NavigationTriggerApp(app: RHMIApplication): NavigationTrigger {
	val event = app.events.values.filterIsInstance<RHMIEvent.ActionEvent>().first {
		it.getAction()?.asLinkAction()?.actionType == "navigate"
	}
	val model = event.getAction()!!.asLinkAction()!!.getLinkModel()!!.asRaDataModel()!!

	override fun triggerNavigation(rhmiNav: String) {
		try {
			model.value = rhmiNav
			event.triggerEvent()
		} catch (e: Exception) {
			Log.i(TAG, "Error while starting navigation", e)
		}
	}
}

class NavigationTriggerSender(val context: Context): NavigationTrigger {
	override fun triggerNavigation(rhmiNav: String) {
		val intent = Intent(NavigationTriggerReceiver.INTENT_NAVIGATION)
				.putExtra(NavigationTriggerReceiver.EXTRA_NAVIGATION, rhmiNav)
		context.sendBroadcast(intent)
	}
}

class NavigationTriggerReceiver(val trigger: NavigationTrigger): BroadcastReceiver() {
	companion object {
		const val INTENT_NAVIGATION = "me.hufman.androidautoidrive.TRIGGER_NAVIGATION"
		const val EXTRA_NAVIGATION = "NAVIGATION"
	}
	override fun onReceive(p0: Context?, p1: Intent?) {
		if (p1?.hasExtra(EXTRA_NAVIGATION) == true) {
			trigger.triggerNavigation(p1.getStringExtra(EXTRA_NAVIGATION))
		}
	}

	fun register(context: Context, handler: Handler) {
		context.registerReceiver(this, IntentFilter(INTENT_NAVIGATION), null, handler)
	}

	fun unregister(context: Context) {
		context.unregisterReceiver(this)
	}
}