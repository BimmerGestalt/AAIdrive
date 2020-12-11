package me.hufman.androidautoidrive.carapp.maps

import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.carapp.FullImageConfigAutosize

class MapAppMode(val capabilities: Map<String, String?>, val appSettings: AppSettings): FullImageConfigAutosize(
		rhmiWidth = capabilities["hmi.display-width"]?.toIntOrNull() ?: 800,
		rhmiHeight = capabilities["hmi.display-height"]?.toIntOrNull() ?: 480) {

	override val isWidescreen: Boolean
		get() = appSettings[AppSettings.KEYS.MAP_WIDESCREEN].toBoolean()
	override val invertScroll: Boolean
		get() = appSettings[AppSettings.KEYS.MAP_INVERT_SCROLL].toBoolean()
}