package me.hufman.androidautoidrive.carapp.maps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import me.hufman.androidautoidrive.maps.LatLong
import me.hufman.androidautoidrive.utils.getSerializableExtraCompat

const val INTENT_INTERACTION = "me.hufman.androidautoidrive.maps.INTERACTION"
const val EXTRA_INTERACTION_TYPE = "me.hufman.androidautoidrive.maps.INTERACTION.TYPE"
const val INTERACTION_SHOW_MAP = "me.hufman.androidautoidrive.maps.INTERACTION.SHOW_MAP"
const val INTERACTION_PAUSE_MAP = "me.hufman.androidautoidrive.maps.INTERACTION.PAUSE_MAP"
const val INTERACTION_ZOOM_IN = "me.hufman.androidautoidrive.maps.INTERACTION.ZOOM_IN"
const val INTERACTION_ZOOM_OUT = "me.hufman.androidautoidrive.maps.INTERACTION.ZOOM_OUT"
const val INTERACTION_SEARCH = "me.hufman.androidautoidrive.maps.INTERACTION.SEARCH"
const val INTERACTION_SEARCH_DETAILS = "me.hufman.androidautoidrive.maps.INTERACTION.SEARCH_DETAILS"
const val INTERACTION_NAV_START = "me.hufman.androidautoidrive.maps.INTERACTION.NAV_START"
const val INTERACTION_NAV_RECALCULATE = "me.hufman.androidautoidrive.maps.INTERACTION.NAV_RECALCULATE"
const val INTERACTION_NAV_STOP = "me.hufman.androidautoidrive.maps.INTERACTION.NAV_STOP"
const val EXTRA_ZOOM_AMOUNT = "me.hufman.androidautoidrive.maps.INTERACTION.ZOOM_AMOUNT"
const val EXTRA_QUERY = "me.hufman.androidautoidrive.maps.INTERACTION.QUERY"
const val EXTRA_ID = "me.hufman.androidautoidrive.maps.INTERACTION.ID"
const val EXTRA_LATLONG = "me.hufman.androidautoidrive.maps.INTERACTION.LATLONG"

const val NAVIGATION_MAP_STARTZOOM_TIME = 4000
const val NAVIGATION_MAP_STARTZOOM_PADDING = 50

interface MapInteractionController {
	fun showMap()
	fun pauseMap()
	fun zoomIn(steps: Int = 1)
	fun zoomOut(steps: Int = 1)
	fun navigateTo(dest: LatLong)
	fun recalcNavigation()
	fun stopNavigation()
}

class MapInteractionControllerIntent(val context: Context): MapInteractionController {
	/** Used by the Car App to send interactions to the map in a different thread */
	private fun send(type: String, extras: Bundle = Bundle()) {
		val intent = Intent(INTENT_INTERACTION)
		intent.`package` = context.packageName
		intent.putExtras(extras)
		intent.putExtra(EXTRA_INTERACTION_TYPE, type)
		context.sendBroadcast(intent)
	}

	override fun showMap() {
		send(INTERACTION_SHOW_MAP)
	}

	override fun pauseMap() {
		send(INTERACTION_PAUSE_MAP)
	}

	override fun zoomIn(steps: Int) {
		send(INTERACTION_ZOOM_IN, Bundle().apply { putInt(EXTRA_ZOOM_AMOUNT, steps) })
	}

	override fun zoomOut(steps: Int) {
		send(INTERACTION_ZOOM_OUT, Bundle().apply { putInt(EXTRA_ZOOM_AMOUNT, steps) })
	}

	override fun navigateTo(dest: LatLong) {
		send(INTERACTION_NAV_START, Bundle().apply { putSerializable(EXTRA_LATLONG, dest) })
	}

	override fun recalcNavigation() {
		send(INTERACTION_NAV_RECALCULATE)
	}
	override fun stopNavigation() {
		send(INTERACTION_NAV_STOP)
	}
}


class MapsInteractionControllerListener(val context: Context, val controller: MapInteractionController) {
	val TAG = "MapControllerListener"

	/** Registers for interaction intents and routes requests to the controller methods */
	private val interactionListener = InteractionListener()

	fun onCreate() {
		context.registerReceiver(interactionListener, IntentFilter(INTENT_INTERACTION))
	}

	inner class InteractionListener: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			if (context == null || intent == null || context.packageName != intent.`package`) return
			Log.i(TAG, "Received interaction: ${intent.action}/${intent.getStringExtra(EXTRA_INTERACTION_TYPE)}")
			if (intent.action != INTENT_INTERACTION) return

			when (intent.getStringExtra(EXTRA_INTERACTION_TYPE)) {
				INTERACTION_SHOW_MAP -> controller.showMap()
				INTERACTION_PAUSE_MAP -> controller.pauseMap()
				INTERACTION_ZOOM_IN -> controller.zoomIn(intent.getIntExtra(EXTRA_ZOOM_AMOUNT, 1))
				INTERACTION_ZOOM_OUT -> controller.zoomOut(intent.getIntExtra(EXTRA_ZOOM_AMOUNT, 1))
				INTERACTION_NAV_START -> controller.navigateTo(intent.getSerializableExtraCompat(EXTRA_LATLONG) as? LatLong
						?: return)
				INTERACTION_NAV_RECALCULATE -> controller.recalcNavigation()
				INTERACTION_NAV_STOP -> controller.stopNavigation()
				else -> Log.i(TAG, "Unknown interaction ${intent.getStringExtra(EXTRA_INTERACTION_TYPE)}")
			}
		}
	}

	fun onDestroy() {
		try {
			context.unregisterReceiver(interactionListener)
		} catch (e: IllegalArgumentException) {}
		controller.pauseMap()
	}
}

