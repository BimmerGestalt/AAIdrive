package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BooleanLiveSetting
import me.hufman.androidautoidrive.BuildConfig
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.combine

class MapPageModel(appContext: Context): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return MapPageModel(appContext) as T
		}
	}
	val mapEnabled = BooleanLiveSetting(appContext, AppSettings.KEYS.ENABLED_MAPS)
	val testMapSupported
		get() = BuildConfig.DEBUG && BuildConfig.FLAVOR_map == "mapbox"
	val testMapEnabled = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_TESTING_ENABLED)
	val mapSettingsShowing = mapEnabled.combine(testMapEnabled) { mapEnabled, testMapEnabled ->
		mapEnabled || (testMapSupported && testMapEnabled)
	}
}