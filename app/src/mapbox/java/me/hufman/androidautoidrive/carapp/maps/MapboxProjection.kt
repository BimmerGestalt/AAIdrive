package me.hufman.androidautoidrive.carapp.maps

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.StyleExtensionImpl
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.match
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.eq
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.get
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.interpolate
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.literal
import com.mapbox.maps.extension.style.layers.generated.fillExtrusionLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.light.generated.light
import com.mapbox.maps.extension.style.sources.generated.vectorSource
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.scalebar.scalebar
import io.bimmergestalt.idriveconnectkit.SidebarRHMIDimensions
import io.bimmergestalt.idriveconnectkit.SubsetRHMIDimensions
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.utils.Utils

@SuppressLint("Lifecycle")
class MapboxProjection(val parentContext: Context, display: Display, private val appSettings: AppSettings,
                       private val locationProvider: MapboxLocationSource): Presentation(parentContext, display) {

	val TAG = "MapboxProjection"
	val map: MapView by lazy { findViewById(R.id.mapView) }
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
		applyCommonSettings()
		mapListener?.run()
	}

	@SuppressLint("RtlHardcoded")
	/** Display settings that don't change based on user settings */
	fun applyCommonSettings() {
		map.compass.updateSettings {
			fadeWhenFacingNorth = false
			position = Gravity.BOTTOM or Gravity.RIGHT
			enabled = true
		}
		map.location.updateSettings {
			map.location.setLocationProvider(locationProvider)
			map.location.locationPuck = LocationPuck2D(
					bearingImage = AppCompatResources.getDrawable(context, R.drawable.ic_mapbox_bearing),
					shadowImage = AppCompatResources.getDrawable(context, R.drawable.ic_mapbox_shadow)
			)
			map.location.enabled = true
		}
		map.scalebar.updateSettings {
			// it seems that the scalebar ignores the map padding, so we must center it
			position = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
			ratio = 0.25f
			textSize = 16f
		}
	}

	/** Call this function whenever we think the settings have been changed and need to be applied */
	fun applySettings(settings: MapboxSettings) {
		// the narrow-screen option centers the viewport to the middle of the display
		// so update the map's margin to match
		val margin = (fullDimensions.appWidth - sidebarDimensions.appWidth) / 2
		map.setPadding(margin, fullDimensions.paddingTop, margin, 0)

		map.getMapboxMap().loadStyle(style(settings.mapStyleUri) {
			applyCommonSettings()

			if (settings.mapTraffic) {
				drawTraffic(this, settings)
			}

			if (settings.mapBuildings) {
				drawBuildings(this)
			}
		})
	}

	fun drawTraffic(style: StyleExtensionImpl.Builder, settings: MapboxSettings) {
		val darkMode = !settings.mapDaytime
		style.apply {
			+vectorSource("mapbox-traffic") {
				url("mapbox://mapbox.mapbox-traffic-v1")
			}
			val trafficLayer = lineLayer("traffic-layer", "mapbox-traffic") {
				// style converted from https://api.mapbox.com/styles/v1/mapbox/traffic-day-v2
				// dark mode from https://api.mapbox.com/styles/v1/mapbox/traffic-night-v2
				// couldn't figure out how to do switchCase for the varying line widths, so we have some matches instead
				sourceLayer("traffic")
				lineJoin(LineJoin.ROUND)
				lineOffset(interpolate {
					exponential { literal(1.75) }
					zoom()
					stop {
						literal(7)
						literal(0.3)
					}
					stop {
						literal(18)
						literal(6)
					}
					stop {
						literal(22)
						literal(100)
					}
				})

				lineWidth(interpolate {
					exponential { literal(1.5) }
					zoom()
					stop {
						literal(10)
						match {
							get("class")
							stop { literal("motorway"); literal(1.5) }
							stop { literal("trunk"); literal(1.5) }
							stop { literal("primary"); literal(1.5) }
							stop { literal("primary_link"); literal(0.6) }
							stop { literal("secondary"); literal(0.6) }
							stop { literal("tertiary"); literal(0.6) }
							literal(0)
						}
					}
					stop {
						literal(15)
						match {
							get("class")
							stop { literal("motorway"); literal(8) }
							stop { literal("trunk"); literal(8) }
							stop { literal("primary"); literal(8) }
							stop { literal("primary_link"); literal(2.75) }
							stop { literal("secondary"); literal(2.75) }
							stop { literal("tertiary"); literal(2.75) }
							literal(1.5)
						}
					}
					stop {
						literal(18)
						match {
							get("class")
							stop { literal("motorway"); literal(16) }
							stop { literal("trunk"); literal(16) }
							stop { literal("primary"); literal(16) }
							stop { literal("primary_link"); literal(7) }
							stop { literal("secondary"); literal(7) }
							stop { literal("tertiary"); literal(7) }
							literal(2.75)
						}
					}
					stop {
						literal(22)
						literal(37.5)
					}
				})
				lineColor(match {
					get("congestion")
					stop {
						literal("low")
						if (darkMode)
							color(Color.parseColor("#1D4E32"))
						else
							color(Color.parseColor("#4DCB82"))
					}
					stop {
						literal("moderate")
						if (darkMode)
							color(Color.parseColor("#8D552A"))
						else
							color(Color.parseColor("#FFA34D"))
					}
					stop {
						literal("heavy")
						if (darkMode)
							color(Color.parseColor("#A72A2A"))
						else
							color(Color.parseColor("#E64747"))
					}
					stop {
						literal("severe")
						if (darkMode)
							color(Color.parseColor("#80001A"))
						else
							color(Color.parseColor("#99001A"))
					}
					rgba(0.0, 0.0, 0.0, 0.0)
				})
			}
			// only add below the road-label if we are using an official layer with road-labels
			when (settings.mapStyleUri) {
				Style.MAPBOX_STREETS -> +layerAtPosition(trafficLayer, below = "road-label")
				Style.SATELLITE_STREETS -> +layerAtPosition(trafficLayer, below = "road-label")
				MapboxSettings.MAPBOX_GUIDANCE_NIGHT -> +layerAtPosition(trafficLayer, below = "road-label-small")
				else -> +trafficLayer
			}
		}
	}

	fun drawBuildings(style: StyleExtensionImpl.Builder) {
		style.apply {
			+fillExtrusionLayer("3d-buildings", "composite") {
				sourceLayer("building")
				filter(eq(get("extrude"), literal("true")))
				minZoom(15.0)
				fillExtrusionColor("#aaaaaa")
				fillExtrusionHeight(get("height"))
				fillExtrusionBase(get("min_height"))
				fillExtrusionOpacity(0.6)
			}
			+light {
				position(1.15, 210.0, 30.0)
			}
		}
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
	}
}