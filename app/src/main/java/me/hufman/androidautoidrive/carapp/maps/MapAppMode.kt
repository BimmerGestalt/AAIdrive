package me.hufman.androidautoidrive.carapp.maps

import android.graphics.Bitmap
import androidx.lifecycle.MutableLiveData
import com.google.gson.JsonObject
import io.bimmergestalt.idriveconnectkit.CDS
import io.bimmergestalt.idriveconnectkit.CDSProperty
import io.bimmergestalt.idriveconnectkit.RHMIDimensions
import io.bimmergestalt.idriveconnectkit.SidebarRHMIDimensions
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BuildConfig
import me.hufman.androidautoidrive.MutableAppSettingsObserver
import me.hufman.androidautoidrive.carapp.CDSData
import me.hufman.androidautoidrive.carapp.CDSEventHandler
import me.hufman.androidautoidrive.carapp.CDSVehicleUnits
import me.hufman.androidautoidrive.carapp.FullImageConfig
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.maps.LatLong
import kotlin.math.max

class DynamicScreenCaptureConfig(val fullDimensions: RHMIDimensions,
                                 val carTransport: MusicAppMode.TRANSPORT_PORTS,
                                 val timeProvider: () -> Long = {System.currentTimeMillis()}): ScreenCaptureConfig {
	companion object {
		const val RECENT_INTERACTION_THRESHOLD = 5000
	}

	override val maxWidth: Int = fullDimensions.visibleWidth
	override val maxHeight: Int = fullDimensions.visibleHeight
	override val compressFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
	override val compressQuality: Int
		get() {
			val recentInteraction = recentInteractionUntil > timeProvider()
			return if (carTransport == MusicAppMode.TRANSPORT_PORTS.USB) {
				if (recentInteraction) 40 else 65
			} else {
				if (recentInteraction) 12 else 40
			}
		}

	var recentInteractionUntil: Long = 0
		private set

	fun startInteraction(timeoutMs: Int = DynamicScreenCaptureConfig.RECENT_INTERACTION_THRESHOLD) {
		recentInteractionUntil = max(recentInteractionUntil, timeProvider() + timeoutMs)
	}
}

class MapAppMode(val fullDimensions: RHMIDimensions,
                 val appSettings: MutableAppSettingsObserver,
                 val cdsData: CDSData,
                 val screenCaptureConfig: DynamicScreenCaptureConfig): FullImageConfig, ScreenCaptureConfig by screenCaptureConfig {
	companion object {
		// whether the custom map is currently navigating somewhere
		private var currentNavDestination: LatLong? = null
			set(value) {
				currentNavDestinationObservable.postValue(value)
				field = value
			}
		private val currentNavDestinationObservable = MutableLiveData<LatLong>()

		fun build(fullDimensions: RHMIDimensions,
		          appSettings: MutableAppSettingsObserver,
		          cdsData: CDSData,
		          carTransport: MusicAppMode.TRANSPORT_PORTS): MapAppMode {
			val screenCaptureConfig = DynamicScreenCaptureConfig(fullDimensions, carTransport)
			return MapAppMode(fullDimensions, appSettings, cdsData, screenCaptureConfig)
		}
	}

	init {
		cdsData.addEventHandler(CDS.VEHICLE.UNITS, 10000, object: CDSEventHandler {
			override fun onPropertyChangedEvent(property: CDSProperty, propertyValue: JsonObject) {
				// just subscribing in order to ensure that distanceUnits is updated
			}
		})
	}

	// current navigation status, for the UI to observe
	// wraps the static fields so that mock MapAppMode objects can be passed around for testing
	var currentNavDestination: LatLong?
		get() = MapAppMode.currentNavDestination
		set(value) {
			MapAppMode.currentNavDestination = value
		}
	val currentNavDestinationObservable: MutableLiveData<LatLong>
		get() = MapAppMode.currentNavDestinationObservable

	// navigation distance units
	val distanceUnits: CDSVehicleUnits.Distance
		get() = CDSVehicleUnits.fromCdsProperty(cdsData[CDSProperty.VEHICLE_UNITS]).distanceUnits

	// toggleable settings
	val settings = listOfNotNull(
			// only show the Widescreen option if the car screen is wide
			if (fullDimensions.rhmiWidth >= 1000)        // RHMIDimensions widescreen cut-off
				AppSettings.KEYS.MAP_WIDESCREEN else null
			) + MapToggleSettings.settings + listOfNotNull(
			// add the Mapbox style toggle if it is filled in
			if (BuildConfig.FLAVOR_map=="mapbox" && appSettings[AppSettings.KEYS.MAPBOX_STYLE_URL].isNotBlank())
				AppSettings.KEYS.MAP_CUSTOM_STYLE else null
	)

	// the current appDimensions depending on the widescreen setting
	val appDimensions = SidebarRHMIDimensions(fullDimensions) {isWidescreen}

	// the screen dimensions used by FullImageConfig
	// FullImageConfig uses rhmiDimensions.width/height to set the image capture region
	override val rhmiDimensions = appDimensions

	val isWidescreen: Boolean
		get() = appSettings[AppSettings.KEYS.MAP_WIDESCREEN].toBoolean()
	override val invertScroll: Boolean
		get() = appSettings[AppSettings.KEYS.MAP_INVERT_SCROLL].toBoolean()

	// screen capture quality adjustment
	fun startInteraction(timeoutMs: Int = DynamicScreenCaptureConfig.RECENT_INTERACTION_THRESHOLD) {
		screenCaptureConfig.startInteraction(timeoutMs)
	}
}