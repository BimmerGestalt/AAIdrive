package me.hufman.androidautoidrive.carapp.navigation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import com.google.openlocationcode.OpenLocationCode
import me.hufman.androidautoidrive.carapp.navigation.NavigationTrigger.Companion.TAG
import me.hufman.androidautoidrive.carapp.maps.LatLong
import me.hufman.idriveconnectionkit.rhmi.RHMIApplication
import me.hufman.idriveconnectionkit.rhmi.RHMIEvent
import java.lang.IllegalArgumentException

interface NavigationTrigger {
	companion object {
		val TAG = "NavTrigger"
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