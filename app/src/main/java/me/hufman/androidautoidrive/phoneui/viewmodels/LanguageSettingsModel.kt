package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.CDSVehicleLanguage
import me.hufman.androidautoidrive.carapp.liveData
import me.hufman.androidautoidrive.phoneui.map
import me.hufman.idriveconnectionkit.CDS
import java.lang.Exception

class LanguageSettingsModel(appContext: Context, carInformation: CarInformation): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return LanguageSettingsModel(appContext, CarInformationObserver()) as T
		}
	}

	val carLocale = carInformation.cachedCdsData.liveData[CDS.VEHICLE.LANGUAGE].map(null, {
		try {
			CDSVehicleLanguage.fromCdsProperty(it).locale
		} catch (e: Exception) { null }
	})
	val lblPreferCarLanguage: LiveData<Context.() -> String> = Transformations.map(carLocale) {
		if (it == null || it == CDSVehicleLanguage.NONE.locale) {
			{ getString(R.string.lbl_language_prefercar) }
		} else {
			{ getString(R.string.lbl_language_prefercar_code, it.toLanguageTag()) }
		}
	}

	val showAdvanced = BooleanLiveSetting(appContext, AppSettings.KEYS.SHOW_ADVANCED_SETTINGS)
	val preferCarLanguage = BooleanLiveSetting(appContext, AppSettings.KEYS.PREFER_CAR_LANGUAGE)
	val forceCarLanguage = StringLiveSetting(appContext, AppSettings.KEYS.FORCE_CAR_LANGUAGE)
}