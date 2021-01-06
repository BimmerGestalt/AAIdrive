package me.hufman.androidautoidrive.carapp.maps

import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.carapp.FullImageConfig
import me.hufman.androidautoidrive.carapp.RHMIDimensions
import me.hufman.androidautoidrive.carapp.SidebarRHMIDimensions

class MapAppMode(val fullDimensions: RHMIDimensions, val appSettings: AppSettings): FullImageConfig {
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