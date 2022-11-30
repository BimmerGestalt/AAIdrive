package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import androidx.lifecycle.*
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.maps.GmapKeyValidation
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.map

class MapSettingsModel(appContext: Context, carCapabilitiesSummarized: LiveData<CarCapabilitiesSummarized>): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		val carInformation = CarInformationObserver()

		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			val carCapabilitiesSummarized = MutableLiveData<CarCapabilitiesSummarized>()
			carInformation.callback = {
				carCapabilitiesSummarized.postValue(CarCapabilitiesSummarized(carInformation))
			}
			carCapabilitiesSummarized.value = CarCapabilitiesSummarized(carInformation)

			return MapSettingsModel(appContext, carCapabilitiesSummarized) as T
		}

		fun unsubscribe() {
			carInformation.callback = {}
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

	val mapWidescreenSupported = carCapabilitiesSummarized.map(false) {
		it.mapWidescreenSupported
	}
	val mapWidescreenUnsupported = carCapabilitiesSummarized.map(false) {
		it.mapWidescreenUnsupported
	}
	val mapWidescreenCrashes = carCapabilitiesSummarized.map(false) {
		it.mapWidescreenCrashes
	}

	val invalidKey: LiveData<Boolean?> = liveData {
		emit(GmapKeyValidation(appContext).validateKey() == false)
	}
}