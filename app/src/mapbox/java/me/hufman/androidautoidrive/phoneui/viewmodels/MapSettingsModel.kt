package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BooleanLiveSetting
import me.hufman.androidautoidrive.StringLiveSetting
import me.hufman.androidautoidrive.phoneui.FunctionalLiveData
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.combine

class MapSettingsModel(appContext: Context): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return MapSettingsModel(appContext) as T
		}
	}

	val mapEnabled = BooleanLiveSetting(appContext, AppSettings.KEYS.ENABLED_MAPS)
	val showAdvancedSettings = BooleanLiveSetting(appContext, AppSettings.KEYS.SHOW_ADVANCED_SETTINGS)
	val mapPhoneGps = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_USE_PHONE_GPS)
	val mapWidescreen = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_WIDESCREEN)
	val mapInvertZoom = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_INVERT_SCROLL)
	val mapBuildings = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_BUILDINGS)
	val mapTraffic = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_TRAFFIC)
	val mapboxCustomStyle = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_CUSTOM_STYLE)
	val mapboxStyleUrl = StringLiveSetting(appContext, AppSettings.KEYS.MAPBOX_STYLE_URL)
	val showMapboxCustomField = FunctionalLiveData {
		// use a FunctionalLiveData so that
		// we keep showing this field if it was set at onResume
		// and while the user toggles it
		showAdvancedSettings.value == true || mapboxCustomStyle.value == true
	}.combine(mapboxCustomStyle) { sticky, enabled ->
		// also show the option if the option is toggled in the car menu
		sticky || enabled
	}
}