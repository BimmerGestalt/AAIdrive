package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BooleanLiveSetting
import me.hufman.androidautoidrive.StringLiveSetting

class MapSettingsModel(appContext: Context): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return MapSettingsModel(appContext) as T
		}
	}

	val mapEnabled = BooleanLiveSetting(appContext, AppSettings.KEYS.ENABLED_GMAPS)
	val mapStyle  = StringLiveSetting(appContext, AppSettings.KEYS.GMAPS_STYLE)
	val mapWidescreen = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_WIDESCREEN)
	val mapInvertZoom = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_INVERT_SCROLL)
	val mapTraffic = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_TRAFFIC)
	val gmapBuildings = BooleanLiveSetting(appContext, AppSettings.KEYS.GMAPS_BUILDINGS)
}