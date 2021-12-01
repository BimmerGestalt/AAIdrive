package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BooleanLiveSetting
import me.hufman.androidautoidrive.phoneui.FunctionalLiveData

class CalendarSettingsModel(appContext: Context): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return CalendarSettingsModel(appContext) as T
		}
	}

	val calendarEnabled = BooleanLiveSetting(appContext, AppSettings.KEYS.ENABLED_CALENDAR)

	val xiaomiCalendarInstalled = FunctionalLiveData<Boolean> {
		try {
			appContext.packageManager.getPackageInfo("com.xiaomi.calendar", 0)
			true
		} catch (e: PackageManager.NameNotFoundException) { false }
	}
}