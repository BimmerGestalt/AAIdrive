package me.hufman.androidautoidrive.carapp.maps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import com.google.android.gms.maps.CameraUpdateFactory
import java.io.ByteArrayOutputStream

const val INTENT_INTERACTION = "me.hufman.androidautoidrive.maps.INTERACTION"
const val EXTRA_INTERACTION_TYPE = "me.hufman.androidautoidrive.maps.INTERACTION.ZOOM_AMOUNT"
const val INTERACTION_SHOW_MAP = "me.hufman.androidautoidrive.maps.INTERACTION.SHOW_MAP"
const val INTERACTION_PAUSE_MAP = "me.hufman.androidautoidrive.maps.INTERACTION.PAUSE_MAP"
const val INTERACTION_ZOOM_IN = "me.hufman.androidautoidrive.maps.INTERACTION.ZOOM_IN"
const val INTERACTION_ZOOM_OUT = "me.hufman.androidautoidrive.maps.INTERACTION.ZOOM_OUT"
const val EXTRA_ZOOM_AMOUNT = "me.hufman.androidautoidrive.maps.INTERACTION.ZOOM_AMOUNT"

interface MapInteractionController {
	fun showMap()
	fun pauseMap()
	fun zoomIn(steps: Int = 1)
	fun zoomOut(steps: Int = 1)
}

class MapInteractionControllerIntent(val context: Context): MapInteractionController {
	/** Used by the Car App to send interactions to the map in a different thread */
	fun send(type: String, extras: Bundle = Bundle()) {
		val intent = Intent(INTENT_INTERACTION)
		intent.putExtras(extras)
		intent.putExtra(EXTRA_INTERACTION_TYPE, type)
		LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
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
}


abstract class MapsControllerInteractionListener(val context: Context): MapInteractionController {
	/** Registers for interaction intents and routes requests to subclass methods */
	private val interactionListener = InteractionListener()
	init {
		LocalBroadcastManager.getInstance(context).registerReceiver(interactionListener, IntentFilter(INTENT_INTERACTION))
	}

	inner class InteractionListener: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			if (context == null || intent == null) return
			Log.i(TAG, "Received interaction: ${intent.action}/${intent.getStringExtra(EXTRA_INTERACTION_TYPE)}")
			if (intent.action != INTENT_INTERACTION) return
			when (intent.getStringExtra(EXTRA_INTERACTION_TYPE)) {
				INTERACTION_SHOW_MAP -> showMap()
				INTERACTION_PAUSE_MAP -> pauseMap()
				INTERACTION_ZOOM_IN -> zoomIn(intent.getIntExtra(EXTRA_ZOOM_AMOUNT, 1))
				INTERACTION_ZOOM_OUT -> zoomOut(intent.getIntExtra(EXTRA_ZOOM_AMOUNT, 1))
				else -> Log.i(TAG, "Unknown interaction ${intent.getStringExtra(EXTRA_INTERACTION_TYPE)}")
			}
		}
	}

	fun onDestroy() {
		LocalBroadcastManager.getInstance(context).unregisterReceiver(interactionListener)
		pauseMap()
	}
}

class GMapsController(context: Context, screenCapture: VirtualDisplayScreenCapture): MapsControllerInteractionListener(context) {

	val projection: GMapsProjection = GMapsProjection(context, screenCapture.virtualDisplay.display)

	override fun showMap() {
		Log.i(TAG, "Beginning map projection")
		if (!projection.isShowing) {
			Log.i(TAG, "First showing of the map")
			projection.show()
		}
		projection.map?.animateCamera(CameraUpdateFactory.scrollBy(1f, 1f))
	}

	override fun pauseMap() {
		//projection.dismiss()
	}

	override fun zoomIn(steps: Int) {
		Log.i(TAG, "Zooming map in $steps steps")
		projection.map?.animateCamera(CameraUpdateFactory.zoomBy(steps.toFloat()))
	}
	override fun zoomOut(steps: Int) {
		Log.i(TAG, "Zooming map out $steps steps")
		projection.map?.animateCamera(CameraUpdateFactory.zoomBy(-steps.toFloat()))
	}
}