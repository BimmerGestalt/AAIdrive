package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.AppSettingsViewer
import me.hufman.androidautoidrive.calendar.CalendarEvent
import me.hufman.androidautoidrive.calendar.CalendarProvider
import me.hufman.androidautoidrive.calendar.PhoneCalendar
import java.util.*
import kotlin.collections.ArrayList

class CalendarEventsModel(val provider: CalendarProvider): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return CalendarEventsModel(CalendarProvider(appContext, AppSettingsViewer())) as T
		}
	}

	val calendars: ArrayList<PhoneCalendar> = ArrayList()
	val upcomingEvents: ArrayList<CalendarEvent> = ArrayList()

	fun update() {
		val today = provider.getNow()
		val monthEvents = provider.getEvents(today[Calendar.YEAR], today[Calendar.MONTH] + 1, null)
		val futureEvents = monthEvents.asSequence().filter {
			it.start[Calendar.DAY_OF_MONTH] >= today[Calendar.DAY_OF_MONTH]
		}.take(8)

		calendars.clear()
		calendars.addAll(provider.getCalendars())
		upcomingEvents.clear()
		upcomingEvents.addAll(futureEvents)
	}
}