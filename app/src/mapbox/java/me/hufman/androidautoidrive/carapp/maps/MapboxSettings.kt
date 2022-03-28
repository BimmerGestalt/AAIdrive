package me.hufman.androidautoidrive.carapp.maps

import com.mapbox.maps.Style
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.maps.LatLong
import me.hufman.androidautoidrive.utils.TimeUtils

data class MapboxSettings(
		val mapWidescreen: Boolean,
		val mapDaytime: Boolean,
		val mapBuildings: Boolean,
		val mapTraffic: Boolean,
		val mapSatellite: Boolean,
		val mapTilt: Boolean,
		val mapCustomStyle: Boolean,
		val mapboxStyleUrl: String,
) {
	companion object {
		fun build(appSettings: AppSettings, location: LatLong?): MapboxSettings {
			val daytime = location == null || TimeUtils.getDayMode(location)
			return MapboxSettings(
					appSettings[AppSettings.KEYS.MAP_WIDESCREEN].toBoolean(),
					daytime,
					appSettings[AppSettings.KEYS.MAP_BUILDINGS].toBoolean(),
					appSettings[AppSettings.KEYS.MAP_TRAFFIC].toBoolean(),
					appSettings[AppSettings.KEYS.MAP_SATELLITE].toBoolean(),
					appSettings[AppSettings.KEYS.MAP_TILT].toBoolean(),
					appSettings[AppSettings.KEYS.MAP_CUSTOM_STYLE].toBoolean(),
					appSettings[AppSettings.KEYS.MAPBOX_STYLE_URL],
			)
		}

		const val MAPBOX_GUIDANCE_NIGHT = "mapbox://styles/mapbox/navigation-guidance-night-v4"
	}

	val mapStyleUri: String
		get() = if (mapCustomStyle && mapboxStyleUrl.isNotBlank()) {
			mapboxStyleUrl
		} else if (mapSatellite) {
			Style.SATELLITE_STREETS
		} else {
			if (mapDaytime) Style.MAPBOX_STREETS else MAPBOX_GUIDANCE_NIGHT
		}
}
