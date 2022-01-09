package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BooleanLiveSetting
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.phoneui.FunctionalLiveData
import java.util.*

class CalendarSettingsModel(appContext: Context, carInformation: CarInformation): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return CalendarSettingsModel(appContext, CarInformation()) as T
		}
	}

	val calendarEnabled = BooleanLiveSetting(appContext, AppSettings.KEYS.ENABLED_CALENDAR)
	val detailedEvents = BooleanLiveSetting(appContext, AppSettings.KEYS.CALENDAR_DETAILED_EVENTS)
	val autonav = BooleanLiveSetting(appContext, AppSettings.KEYS.CALENDAR_AUTOMATIC_NAVIGATION)
	val isNaviNotSupported = FunctionalLiveData {
		carInformation.capabilities["navi"]?.lowercase(Locale.ROOT) == "false"
	}

	val xiaomiCalendarInstalled = FunctionalLiveData<Boolean> {
		try {
			appContext.packageManager.getPackageInfo("com.xiaomi.calendar", 0)
			true
		} catch (e: PackageManager.NameNotFoundException) { false }
	}
}