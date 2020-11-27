package me.hufman.androidautoidrive.carapp.maps

import android.Manifest
import android.app.Presentation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.Display
import android.view.WindowManager
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import kotlinx.android.synthetic.gmap.gmaps_projection.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.INTENT_GMAP_RELOAD_SETTINGS
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.TimeUtils

class GMapsProjection(val parentContext: Context, display: Display): Presentation(parentContext, display) {
	val TAG = "GMapsProjection"
	var map: GoogleMap? = null
	var mapListener: Runnable? = null
	val settingsListener = SettingsReload()
	var currentStyleId: Int? = null
	var location: LatLng? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		window?.setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION)
		setContentView(R.layout.gmaps_projection)

		gmapView.onCreate(savedInstanceState)
		gmapView.getMapAsync { map ->
			this.map = map

			// load initial theme settings for the map, location might not be loaded yet though
			applySettings()

			map.isIndoorEnabled = false
			map.isTrafficEnabled = true
			map.setPadding(150, 0, 150, 0)

			if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
				map.isMyLocationEnabled = true
			}

			with (map.uiSettings) {
				isCompassEnabled = true
				isMyLocationButtonEnabled = false
			}

			mapListener?.run()

		}

		// watch for map settings
		context.registerReceiver(settingsListener, IntentFilter(INTENT_GMAP_RELOAD_SETTINGS))
	}

	override fun onStart() {
		super.onStart()
		Log.i(TAG, "Projection Start")
		gmapView.onStart()
		gmapView.onResume()

	}

	fun applySettings() {
		val style = AppSettings[AppSettings.KEYS.GMAPS_STYLE].toLowerCase()

		val location = this.location
		val mapstyleId = when(style) {
			"auto" -> if (location == null || TimeUtils.getDayMode(LatLong(location.latitude, location.longitude))) null else R.raw.gmaps_style_night
			"night" -> R.raw.gmaps_style_night
			"aubergine" -> R.raw.gmaps_style_aubergine
			"midnight_commander" -> R.raw.gmaps_style_midnight_commander
			else -> null
		}
		if (mapstyleId != currentStyleId) {
			Log.i(TAG, "Setting gmap style to $style")
			val mapstyle = if (mapstyleId != null) MapStyleOptions.loadRawResourceStyle(parentContext, mapstyleId) else null
			map?.setMapStyle(mapstyle)
		}
		currentStyleId = mapstyleId
	}

	override fun onStop() {
		super.onStop()
		Log.i(TAG, "Projection Stopped")
		gmapView.onPause()
		gmapView.onStop()
		gmapView.onDestroy()
		context.unregisterReceiver(settingsListener)
	}

	override fun onSaveInstanceState(): Bundle {
		val output = super.onSaveInstanceState()
		gmapView.onSaveInstanceState(output)
		return output
	}


	inner class SettingsReload: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			if (intent?.action == INTENT_GMAP_RELOAD_SETTINGS) {
				// reload any settings that were changed in the UI
				applySettings()
			}
		}
	}
}