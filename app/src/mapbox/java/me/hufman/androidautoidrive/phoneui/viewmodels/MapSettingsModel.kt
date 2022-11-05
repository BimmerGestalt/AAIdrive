package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import androidx.lifecycle.*
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.maps.MapboxTokenValidation
import me.hufman.androidautoidrive.phoneui.FunctionalLiveData
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.combine
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

	val mapEnabled = BooleanLiveSetting(appContext, AppSettings.KEYS.ENABLED_MAPS)
	val showAdvancedSettings = BooleanLiveSetting(appContext, AppSettings.KEYS.SHOW_ADVANCED_SETTINGS)
	val mapPhoneGps = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_USE_PHONE_GPS)
	val mapWidescreen = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_WIDESCREEN)
	val mapInvertZoom = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_INVERT_SCROLL)
	val mapTilt = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_TILT)
	val mapBuildings = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_BUILDINGS)
	val mapSatellite = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_SATELLITE)
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

	val mapWidescreenSupported = carCapabilitiesSummarized.map(false) {
		it.mapWidescreenSupported
	}
	val mapWidescreenUnsupported = carCapabilitiesSummarized.map(false) {
		it.mapWidescreenUnsupported
	}
	val mapWidescreenCrashes = carCapabilitiesSummarized.map(false) {
		it.mapWidescreenCrashes
	}

	val invalidAccessToken: LiveData<Boolean?> = liveData {
		// only need to check the Access Token, the Download Token is only used during build
		emit(MapboxTokenValidation.validateToken(BuildConfig.MapboxAccessToken) == false)
	}
}