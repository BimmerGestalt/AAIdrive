package me.hufman.androidautoidrive.carapp.maps

import androidx.lifecycle.MutableLiveData
import io.bimmergestalt.idriveconnectkit.RHMIDimensions
import io.bimmergestalt.idriveconnectkit.SidebarRHMIDimensions
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettingsObserver
import me.hufman.androidautoidrive.carapp.FullImageConfig
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.maps.LatLong

class MapAppMode(val fullDimensions: RHMIDimensions, val appSettings: MutableAppSettingsObserver, val carTransport: MusicAppMode.TRANSPORT_PORTS): FullImageConfig {
	companion object {
		// whether the custom map is currently navigating somewhere
		private var currentNavDestination: LatLong? = null
			set(value) {
				currentNavDestinationObservable.postValue(value)
				field = value
			}
		private val currentNavDestinationObservable = MutableLiveData<LatLong>()
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

	// toggleable settings
	val settings = MapToggleSettings.settings

	// the default jpg quality to use based on carTransport
	val compressQuality: Int = if (carTransport == MusicAppMode.TRANSPORT_PORTS.USB) 65 else 40

	// the current appDimensions depending on the widescreen setting
	val appDimensions = SidebarRHMIDimensions(fullDimensions) {isWidescreen}

	// the screen dimensions used by FullImageConfig
	// FullImageConfig uses rhmiDimensions.width/height to set the image capture region
	override val rhmiDimensions = appDimensions

	val isWidescreen: Boolean
		get() = appSettings[AppSettings.KEYS.MAP_WIDESCREEN].toBoolean()
	override val invertScroll: Boolean
		get() = appSettings[AppSettings.KEYS.MAP_INVERT_SCROLL].toBoolean()
}