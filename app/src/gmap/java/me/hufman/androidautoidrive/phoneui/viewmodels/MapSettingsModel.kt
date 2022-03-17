package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.liveData
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BooleanLiveSetting
import me.hufman.androidautoidrive.StringLiveSetting
import me.hufman.androidautoidrive.maps.GmapKeyValidation

class MapSettingsModel(appContext: Context): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return MapSettingsModel(appContext) as T
		}
	}

	val showAdvancedSettings = BooleanLiveSetting(appContext, AppSettings.KEYS.SHOW_ADVANCED_SETTINGS)
	val mapPhoneGps = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_USE_PHONE_GPS)
	val mapEnabled = BooleanLiveSetting(appContext, AppSettings.KEYS.ENABLED_MAPS)
	val mapStyle  = StringLiveSetting(appContext, AppSettings.KEYS.GMAPS_STYLE)
	val mapWidescreen = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_WIDESCREEN)
	val mapInvertZoom = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_INVERT_SCROLL)
	val mapBuildings = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_BUILDINGS)
	val mapTraffic = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_TRAFFIC)

	val invalidKey: LiveData<Boolean?> = liveData {
		emit(GmapKeyValidation(appContext).validateKey() == false)
	}
}