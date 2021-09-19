package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BooleanLiveSetting
import me.hufman.androidautoidrive.BuildConfig
import me.hufman.androidautoidrive.phoneui.FunctionalLiveData

class AnalyticsSettingsModel(appContext: Context): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return AnalyticsSettingsModel(appContext) as T
		}
	}

	val supportedAnalytics = FunctionalLiveData {
		BuildConfig.FLAVOR_analytics != "nonalytics"
	}

	val enableAnalytics = BooleanLiveSetting(appContext, AppSettings.KEYS.ENABLED_ANALYTICS)
}