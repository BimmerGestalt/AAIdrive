package me.hufman.androidautoidrive.calendar

import android.location.Address
import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.carapp.calendar.UpcomingDestination
import me.hufman.androidautoidrive.carapp.maps.LatLong
import me.hufman.androidautoidrive.carapp.navigation.AddressSearcher
import me.hufman.androidautoidrive.carapp.navigation.NavigationParser
import me.hufman.androidautoidrive.carapp.navigation.NavigationTrigger
import org.junit.Test
import java.util.*

class UpcomingDestinationTest {

	var calendarEvents = listOf(
			CalendarEvent("A", makeCalendar(2021, 11, 10, 9, 15), makeCalendar(2021, 11, 10, 16, 45), "Place1", "", 0),
			CalendarEvent("B", makeCalendar(2021, 11, 11, 9, 15), makeCalendar(2021, 11, 11, 16, 45), "Place2", "", 0),
			CalendarEvent("C", makeCalendar(2021, 11, 11, 18, 0), makeCalendar(2021, 11, 11, 20, 0), "Place3", "", 0),
			CalendarEvent("Holiday", makeCalendar(2021, 11, 25, 0, 0), makeCalendar(2021, 11, 26, 0, 0), "Place4", "", 0),
			CalendarEvent("Holiday", makeCalendar(2021, 12, 25, 0, 0), makeCalendar(2021, 12, 26, 0, 0), "Place5", "", 0),
			CalendarEvent("Holiday", makeCalendar(2022, 1, 1, 0, 0), makeCalendar(2022, 1, 2, 0, 0), "Place6", "", 0),
	)
	val calendarProvider = mock<CalendarProvider> {
		on {hasPermission()} doReturn true
		on {getNow()} doAnswer { makeCalendar(2021, 11, 11, 9, 0) }
		on {getEvents(any(), any(), isNull())} doAnswer { inv -> calendarEvents.filter { it.start[Calendar.YEAR] == inv.arguments[0] && it.start[Calendar.MONTH] + 1 == inv.arguments[1] } }
		on {getEvents(any(), any(), isNotNull())} doAnswer { inv -> calendarEvents.filter { it.start[Calendar.YEAR] == inv.arguments[0] && it.start[Calendar.MONTH] + 1 == inv.arguments[1] && it.start[Calendar.DAY_OF_MONTH] == inv.arguments[2] } }
	}
	val addressSearcher = mock<AddressSearcher> {
		on {search(any())} doReturn null
	}
	val navigationTrigger = mock<NavigationTrigger>()

	val currentLatLong = LatLong(37.373022, -121.994893)
	val closeLatLong = LatLong(37.373347, -121.994520)      // too close to navigation to
	val farLatLong = LatLong(37.353052, -121.976596)
	val otherLatLong = LatLong(37.352048, -121.960684)
	val closeAddress = mock<Address> {
		on {latitude} doReturn closeLatLong.latitude
		on {longitude} doReturn closeLatLong.longitude
	}
	val farAddress = mock<Address> {
		on {latitude} doReturn farLatLong.latitude
		on {longitude} doReturn farLatLong.longitude
	}
	val otherAddress = mock<Address> {
		on {latitude} doReturn otherLatLong.latitude
		on {longitude} doReturn otherLatLong.longitude
	}

	val upcomingDestination = UpcomingDestination(calendarProvider, addressSearcher, navigationTrigger)

	private fun makeCalendar(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Calendar {
		return Calendar.getInstance().also {
			it[Calendar.YEAR] = year
			it[Calendar.MONTH] = month - 1
			it[Calendar.DAY_OF_MONTH] = day
			it[Calendar.HOUR_OF_DAY] = hour
			it[Calendar.MINUTE] = minute
			it[Calendar.SECOND] = 0
			it[Calendar.MILLISECOND] = 0
		}
	}

	@Test
	fun testEmptyNavigation() {
		whenever(calendarProvider.getEvents(any(), any(), isNotNull())) doAnswer { emptyList() }
		upcomingDestination.navigateUpcomingDestination(currentLatLong)
		verify(calendarProvider).getEvents(2021, 11, 11)
		verifyZeroInteractions(addressSearcher)
		verifyZeroInteractions(navigationTrigger)
	}

	@Test
	fun testStartNavigation() {
		calendarEvents = listOf(
				CalendarEvent("B", makeCalendar(2021, 11, 11, 9, 45), makeCalendar(2021, 11, 11, 16, 45), "Place3", "", 0),
				CalendarEvent("B", makeCalendar(2021, 11, 11, 9, 15), makeCalendar(2021, 11, 11, 16, 45), "Place2", "", 0),
		)
		whenever(addressSearcher.search("Place2")) doReturn farAddress
		whenever(addressSearcher.search("Place3")) doReturn otherAddress
		upcomingDestination.navigateUpcomingDestination(currentLatLong)
		verify(calendarProvider).getEvents(2021, 11, 11)
		verify(addressSearcher).search("Place2")
		verifyNoMoreInteractions(addressSearcher)
		verify(navigationTrigger).triggerNavigation(farAddress)
	}

	@Test
	fun testStartNavigationSkippingMissingAddress() {
		calendarEvents = listOf(
				CalendarEvent("B", makeCalendar(2021, 11, 11, 9, 45), makeCalendar(2021, 11, 11, 16, 45), "Place3", "", 0),
				CalendarEvent("B", makeCalendar(2021, 11, 11, 9, 15), makeCalendar(2021, 11, 11, 16, 45), "Place2", "", 0),
		)
		whenever(addressSearcher.search("Place2")) doReturn null
		whenever(addressSearcher.search("Place3")) doReturn farAddress
		upcomingDestination.navigateUpcomingDestination(currentLatLong)
		verify(calendarProvider).getEvents(2021, 11, 11)
		verify(addressSearcher).search("Place2")
		verify(addressSearcher).search("Place3")
		verify(navigationTrigger).triggerNavigation(farAddress)
	}

	@Test
	fun testSkipTooClose() {
		calendarEvents = listOf(
				CalendarEvent("B", makeCalendar(2021, 11, 11, 9, 15), makeCalendar(2021, 11, 11, 16, 45), "Place2", "", 0),
		)
		whenever(addressSearcher.search(any())) doReturn closeAddress
		upcomingDestination.navigateUpcomingDestination(currentLatLong)
		verify(calendarProvider).getEvents(2021, 11, 11)
		verify(addressSearcher).search(calendarEvents[0].location)
		verifyZeroInteractions(navigationTrigger)
	}

	@Test
	fun testSkipPrevious() {
		calendarEvents = listOf(
				CalendarEvent("B", makeCalendar(2021, 11, 11, 8, 15), makeCalendar(2021, 11, 11, 16, 45), "Place2", "", 0),
		)
		whenever(addressSearcher.search(any())) doReturn farAddress
		upcomingDestination.navigateUpcomingDestination(currentLatLong)
		verify(calendarProvider).getEvents(2021, 11, 11)
		verifyZeroInteractions(addressSearcher)
		verifyZeroInteractions(navigationTrigger)
	}

	@Test
	fun testSkipTooLongAway() {
		calendarEvents = listOf(
				CalendarEvent("B", makeCalendar(2021, 11, 11, 10, 45), makeCalendar(2021, 11, 11, 16, 45), "Place2", "", 0),
		)
		whenever(addressSearcher.search(any())) doReturn farAddress
		upcomingDestination.navigateUpcomingDestination(currentLatLong)
		verify(calendarProvider).getEvents(2021, 11, 11)
		verifyZeroInteractions(addressSearcher)
		verifyZeroInteractions(navigationTrigger)
	}
}