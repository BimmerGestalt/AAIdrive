package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.AppSettingsViewer
import me.hufman.androidautoidrive.BooleanLiveSetting
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.calendar.CalendarProvider
import me.hufman.androidautoidrive.phoneui.FunctionalLiveData
import me.hufman.androidautoidrive.utils.PackageManagerCompat.getPackageInfoCompat
import java.util.*

class CalendarSettingsModel(appContext: Context, carInformation: CarInformation, val calendarProvider: CalendarProvider): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return CalendarSettingsModel(appContext, CarInformation(), CalendarProvider(appContext, AppSettingsViewer())) as T
		}
	}

	val advancedSettings = BooleanLiveSetting(appContext, AppSettings.KEYS.SHOW_ADVANCED_SETTINGS)
	val calendarEnabled = BooleanLiveSetting(appContext, AppSettings.KEYS.ENABLED_CALENDAR)
	val detailedEvents = BooleanLiveSetting(appContext, AppSettings.KEYS.CALENDAR_DETAILED_EVENTS)
	val ignoreVisibility = BooleanLiveSetting(appContext, AppSettings.KEYS.CALENDAR_IGNORE_VISIBILITY)
	val autonav = BooleanLiveSetting(appContext, AppSettings.KEYS.CALENDAR_AUTOMATIC_NAVIGATION)
	val isNaviNotSupported = FunctionalLiveData {
		carInformation.capabilities["navi"]?.lowercase(Locale.ROOT) == "false"
	}
	private val _areCalendarsFound = MutableLiveData(false)
	val areCalendarsFound: LiveData<Boolean> = _areCalendarsFound

	val xiaomiCalendarInstalled = FunctionalLiveData<Boolean> {
		try {
			appContext.packageManager.getPackageInfoCompat("com.xiaomi.calendar", 0)
			true
		} catch (e: PackageManager.NameNotFoundException) { false }
	}

	fun update() {
		_areCalendarsFound.value = calendarProvider.getCalendars().isNotEmpty()
	}
}