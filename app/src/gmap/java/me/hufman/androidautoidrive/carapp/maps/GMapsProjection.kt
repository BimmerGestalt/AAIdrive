package me.hufman.androidautoidrive.carapp.maps

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.WindowManager
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapsSdkInitializedCallback
import com.google.android.gms.maps.model.MapStyleOptions
import io.bimmergestalt.idriveconnectkit.SidebarRHMIDimensions
import io.bimmergestalt.idriveconnectkit.SubsetRHMIDimensions
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.maps.LatLong
import me.hufman.androidautoidrive.utils.TimeUtils
import java.util.*

class GMapsProjection(val parentContext: Context, display: Display, val appSettings: AppSettingsObserver, val locationSource: GMapsLocationSource): Presentation(parentContext, display), OnMapsSdkInitializedCallback {
	val TAG = "GMapsProjection"
	var map: GoogleMap? = null
	var mapListener: Runnable? = null
	var currentStyleId: Int? = null

	val fullDimensions = display.run {
		val dimension = Point()
		display.getSize(dimension)
		SubsetRHMIDimensions(dimension.x, dimension.y)
	}
	val sidebarDimensions = SidebarRHMIDimensions(fullDimensions) {
		appSettings[AppSettings.KEYS.MAP_WIDESCREEN].toBoolean()
	}

	@SuppressLint("MissingPermission")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		// specifically request the new renderer
		// can be removed after the new renderer is the default, Summer of 2022
		MapsInitializer.initialize(context.applicationContext, MapsInitializer.Renderer.LATEST, this);

		window?.setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION)
		setContentView(R.layout.gmaps_projection)

		val gmapView = findViewById<MapView>(R.id.gmapView)
		gmapView.onCreate(savedInstanceState)
		gmapView.getMapAsync { map ->
			this.map = map

			// load initial theme settings for the map, location might not be loaded yet though
			applySettings()

			map.setLocationSource(locationSource)
			map.isMyLocationEnabled = true

			map.isIndoorEnabled = false

			with (map.uiSettings) {
				isCompassEnabled = true
				isMyLocationButtonEnabled = false
			}

			mapListener?.run()
		}
	}

	override fun onStart() {
		super.onStart()
		Log.i(TAG, "Projection Start")
		val gmapView = findViewById<MapView>(R.id.gmapView)
		gmapView.onStart()
		gmapView.onResume()

		// watch for map settings
		appSettings.callback = {applySettings()}
	}

	fun applySettings() {
		// the narrow-screen option centers the viewport to the middle of the display
		// so update the map's margin to match
		val margin = (fullDimensions.appWidth - sidebarDimensions.appWidth) / 2
		map?.setPadding(margin, 0, margin, 0)

		val style = appSettings[AppSettings.KEYS.GMAPS_STYLE].lowercase(Locale.ROOT)

		val location = this.locationSource.location
		val mapstyleId = when(style) {
			"auto" -> if (location == null || TimeUtils.getDayMode(LatLong(location.latitude, location.longitude))) null else R.raw.gmaps_style_night
			"hybrid" -> null
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
		if (style == "hybrid") {
			map?.mapType = GoogleMap.MAP_TYPE_HYBRID
		} else {
			map?.mapType = GoogleMap.MAP_TYPE_NORMAL
		}
		map?.isBuildingsEnabled = appSettings[AppSettings.KEYS.MAP_BUILDINGS] == "true"
		map?.isTrafficEnabled = appSettings[AppSettings.KEYS.MAP_TRAFFIC] == "true"
		currentStyleId = mapstyleId
	}

	override fun onStop() {
		super.onStop()
		Log.i(TAG, "Projection Stopped")
		val gmapView = findViewById<MapView>(R.id.gmapView)
		gmapView.onPause()
		gmapView.onStop()
		gmapView.onDestroy()
		appSettings.callback = null
	}

	override fun onSaveInstanceState(): Bundle {
		val output = super.onSaveInstanceState()
		val gmapView = findViewById<MapView>(R.id.gmapView)
		gmapView.onSaveInstanceState(output)
		return output
	}

	override fun onMapsSdkInitialized(renderer: MapsInitializer.Renderer) {
		when (renderer) {
			MapsInitializer.Renderer.LATEST -> Log.d("MapsDemo", "The latest version of the renderer is used.")
			MapsInitializer.Renderer.LEGACY -> Log.d("MapsDemo", "The legacy version of the renderer is used.")
		}
	}
}