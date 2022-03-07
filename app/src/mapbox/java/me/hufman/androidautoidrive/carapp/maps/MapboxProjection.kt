package me.hufman.androidautoidrive.carapp.maps

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.eq
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.get
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.literal
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.FillExtrusionLayer
import com.mapbox.maps.extension.style.light.generated.getLight
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.scalebar.scalebar
import io.bimmergestalt.idriveconnectkit.SidebarRHMIDimensions
import io.bimmergestalt.idriveconnectkit.SubsetRHMIDimensions
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.maps.LatLong
import me.hufman.androidautoidrive.utils.TimeUtils
import me.hufman.androidautoidrive.utils.Utils

data class MapboxSettings(
		val mapDaytime: Boolean,
		val mapTraffic: Boolean,
		val mapBuildings: Boolean,
		val mapCustomStyle: Boolean,
		val mapboxStyleUrl: String,
) {
	companion object {
		fun build(appSettings: AppSettings, location: LatLong?): MapboxSettings {
			val daytime = location == null || TimeUtils.getDayMode(location)
			return MapboxSettings(
					daytime,
					appSettings[AppSettings.KEYS.MAP_TRAFFIC].toBoolean(),
					appSettings[AppSettings.KEYS.MAP_BUILDINGS].toBoolean(),
					appSettings[AppSettings.KEYS.MAP_CUSTOM_STYLE].toBoolean(),
					appSettings[AppSettings.KEYS.MAPBOX_STYLE_URL],
			)
		}

		const val MAPBOX_GUIDANCE_NIGHT = "mapbox://styles/mapbox/navigation-guidance-night-v4"
	}

	val mapStyleUri: String
		get() = if (mapCustomStyle && mapboxStyleUrl.isNotBlank()) {
			mapboxStyleUrl
		} else {
			if (mapTraffic) {
				if (mapDaytime) Style.TRAFFIC_DAY else Style.TRAFFIC_NIGHT
			} else {
				if (mapDaytime) Style.MAPBOX_STREETS else MAPBOX_GUIDANCE_NIGHT
			}
		}
}

private fun Location.toLatLong(): LatLong = LatLong(this.latitude, this.longitude)

@SuppressLint("Lifecycle")
class MapboxProjection(val parentContext: Context, display: Display, private val appSettings: AppSettingsObserver,
                       private val locationProvider: MapboxLocationSource): Presentation(parentContext, display) {

	val TAG = "MapboxProjection"
	var appliedSettings: MapboxSettings? = null      // last applied settings
	val map by lazy { findViewById<MapView>(R.id.mapView) }
	val iconAnnotations by lazy { map.annotations.createPointAnnotationManager() }
	val lineAnnotations by lazy { map.annotations.createPolylineAnnotationManager() }
	var mapListener: Runnable? = null

	val fullDimensions = display.run {
		val dimension = Point()
		@Suppress("DEPRECATION")
		display.getSize(dimension)
		SubsetRHMIDimensions(dimension.x, dimension.y)
	}
	val sidebarDimensions = SidebarRHMIDimensions(fullDimensions) {
		appSettings[AppSettings.KEYS.MAP_WIDESCREEN].toBoolean()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		window?.setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION)
		setContentView(R.layout.mapbox_projection)
	}

	override fun onStart() {
		super.onStart()
		Log.i(TAG, "Projection Start")
		map.onStart()
		// watch for map settings
		appSettings.callback = {applySettings()}
		applySettings()
		mapListener?.run()
	}

	fun applySettings() {
		// the narrow-screen option centers the viewport to the middle of the display
		// so update the map's margin to match
		val margin = (fullDimensions.appWidth - sidebarDimensions.appWidth) / 2
		map.setPadding(margin, fullDimensions.paddingTop, margin, 0)

		// AppSettings updates every ~10s from cachedCds updates
		// so check if anything relevant has changed before redrawing map
		val currentSettings = MapboxSettings.build(appSettings, this.locationProvider.location?.toLatLong())
		if (appliedSettings != currentSettings) {
			map.getMapboxMap().loadStyleUri(currentSettings.mapStyleUri) {
				map.location.updateSettings {
					map.location.setLocationProvider(locationProvider)
					map.location.enabled = true
				}
				map.scalebar.updateSettings {
					// it seems that the scalebar ignores the map padding, so we must center it
					position = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
					ratio = 0.25f
					textSize = 16f
				}

				if (appSettings[AppSettings.KEYS.MAP_BUILDINGS].toBoolean()) {
					drawBuildings(it)
				}
			}
			appliedSettings = currentSettings
		}
	}

	fun drawBuildings(style: Style) {
		val fillExtrusionLayer = FillExtrusionLayer("3d-buildings", "composite")
		fillExtrusionLayer.sourceLayer("building")
		fillExtrusionLayer.filter(eq(get("extrude"), literal("true")))
		fillExtrusionLayer.minZoom(15.0)
		fillExtrusionLayer.fillExtrusionColor(Color.parseColor("#aaaaaa"))
		fillExtrusionLayer.fillExtrusionHeight(get("height"))
		fillExtrusionLayer.fillExtrusionBase(get("min_height"))
		fillExtrusionLayer.fillExtrusionOpacity(0.6)
		style.addLayer(fillExtrusionLayer)

		val light = style.getLight()
		light.position(1.15, 210.0, 30.0)
	}

	fun drawNavigation(navController: MapboxNavController) {
		iconAnnotations.deleteAll()
		lineAnnotations.deleteAll()

		val destination = navController.currentNavDestination
		Log.i(TAG, "Adding destination $destination")
		if (destination != null) {
			iconAnnotations.create(PointAnnotationOptions()
					.withIconImage(Utils.getBitmap(ContextCompat.getDrawable(context, R.drawable.ic_pin_drop_red_24)!!, 48, 48))
					.withPoint(com.mapbox.geojson.Point.fromLngLat(destination.longitude, destination.latitude))
			)
		}
		val route = navController.currentNavRoute
		Log.i(TAG, "Adding route $route")
		if (route != null) {
			lineAnnotations.create(PolylineAnnotationOptions()
					.withGeometry(route)
					.withLineColor(context.getColor(R.color.mapRouteLine))
					.withLineWidth(4.0)
			)
		}
	}

	override fun onStop() {
		super.onStop()
		Log.i(TAG, "Projection Stopped")
		map.onStop()
		appSettings.callback = null
	}
}