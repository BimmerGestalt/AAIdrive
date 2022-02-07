package me.hufman.androidautoidrive.carapp.maps

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.LocationProvider
import com.mapbox.maps.plugin.locationcomponent.location
import io.bimmergestalt.idriveconnectkit.SidebarRHMIDimensions
import io.bimmergestalt.idriveconnectkit.SubsetRHMIDimensions
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.utils.Utils

@SuppressLint("Lifecycle")
class MapboxProjection(val parentContext: Context, display: Display, private val appSettings: AppSettingsObserver,
                       private val locationProvider: LocationProvider): Presentation(parentContext, display) {

	val TAG = "MapboxProjection"
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
		map.location.setLocationProvider(locationProvider)
		map.location.enabled = true
		// watch for map settings
		appSettings.callback = {applySettings()}
		map.onStart()
		applySettings()
		mapListener?.run()
	}

	fun applySettings() {
		// the narrow-screen option centers the viewport to the middle of the display
		// so update the map's margin to match
		val margin = (fullDimensions.appWidth - sidebarDimensions.appWidth) / 2 + 30
		map.setPadding(margin, fullDimensions.paddingTop, margin, 0)
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