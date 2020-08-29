package me.hufman.androidautoidrive.carapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import me.hufman.androidautoidrive.carapp.NavigationTrigger.Companion.TAG
import me.hufman.androidautoidrive.carapp.maps.LatLong
import me.hufman.idriveconnectionkit.rhmi.RHMIApplication
import me.hufman.idriveconnectionkit.rhmi.RHMIEvent

interface NavigationTrigger {
	companion object {
		val TAG = "NavTrigger"
		val LATLNG_MATCHER = Regex("^([-0-9.]+,[-0-9.]+).*")
		val LATLNG_LABEL_MATCHER = Regex("^q=([-0-9.]+,[-0-9.]+)(,[-0-9.]+)?\\s*(\\(.*\\))?$")

		fun parseGeoUrl(url: String?): String? {
			url ?: return null
			if (!url.startsWith("geo:")) return null
			val data = url.substring(4)
			val query = if (data.contains('?')) { data.split('?', limit=2)[1] } else null

			val authority_result = LATLNG_MATCHER.matchEntire(data)
			val query_result = LATLNG_LABEL_MATCHER.matchEntire(query ?: "")

			val latlng = query_result?.groupValues?.getOrNull(1) ?: authority_result?.groupValues?.getOrNull(1)
			val latlong = if (latlng?.contains(',') == true) {
				val splits = latlng.split(',')
				LatLong(splits[0].toDouble() / 360 * Int.MAX_VALUE * 2, splits[1].toDouble() / 360 * Int.MAX_VALUE * 2)
			} else null
			val label = query_result?.groupValues?.getOrNull(3)?.replace(";", "") ?: ""

			return if (latlong != null) {
				// [lastName];[firstName];[street];[houseNumber];[zipCode];[city];[country];[latitude];[longitude];[poiName]
				";;;;;;;${latlong.latitude.toBigDecimal()};${latlong.longitude.toBigDecimal()};$label"
			} else {
				null
			}
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