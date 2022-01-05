package me.hufman.androidautoidrive.carapp.calendar

import android.location.Address
import me.hufman.androidautoidrive.calendar.CalendarProvider
import me.hufman.androidautoidrive.calendar.copy
import me.hufman.androidautoidrive.carapp.maps.LatLong
import me.hufman.androidautoidrive.carapp.navigation.AddressSearcher
import me.hufman.androidautoidrive.carapp.navigation.NavigationParser
import me.hufman.androidautoidrive.carapp.navigation.NavigationTrigger
import java.util.*

class UpcomingDestination(val calendarProvider: CalendarProvider, val addressSearcher: AddressSearcher, val navigationTrigger: NavigationTrigger) {
	var currentlyNavigating = false

	fun getUpcomingDestination(currentPosition: LatLong): Address? {
		val now = calendarProvider.getNow()
		val threshold = now.copy()
		threshold.add(Calendar.MINUTE, 90)
		val todayEvents = calendarProvider.getEvents(now[Calendar.YEAR], now[Calendar.MONTH] + 1, now[Calendar.DAY_OF_MONTH])
		val upcomingEvents = todayEvents.filter { it.start > now && it.start < threshold && it.location.isNotBlank() }
				.sortedBy { it.start }
		return upcomingEvents.asSequence().map { addressSearcher.search(it.location) }.filterNotNull()
				.filter { currentPosition.distanceFrom(LatLong(it.latitude, it.longitude)) > 1.0 }
				.firstOrNull()
	}

	fun navigateUpcomingDestination(currentPosition: LatLong) {
		val upcomingDestination = getUpcomingDestination(currentPosition)
		if (!currentlyNavigating && upcomingDestination != null) {
			navigationTrigger.triggerNavigation(NavigationParser.addressToRHMI(upcomingDestination))
		}
	}
}