package me.hufman.androidautoidrive.calendar

import android.database.Cursor
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import me.hufman.androidautoidrive.calendar.CalendarProvider.Companion.INDEX_ALL_DAY
import me.hufman.androidautoidrive.calendar.CalendarProvider.Companion.INDEX_BEGIN
import me.hufman.androidautoidrive.calendar.CalendarProvider.Companion.INDEX_DESCRIPTION
import me.hufman.androidautoidrive.calendar.CalendarProvider.Companion.INDEX_END
import me.hufman.androidautoidrive.calendar.CalendarProvider.Companion.INDEX_LOCATION
import me.hufman.androidautoidrive.calendar.CalendarProvider.Companion.INDEX_TIMEZONE
import me.hufman.androidautoidrive.calendar.CalendarProvider.Companion.INDEX_TITLE
import me.hufman.androidautoidrive.calendar.CalendarProvider.Companion.splitEventIntoDays
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

class CalendarParsingTest {
	val timeZone: TimeZone = TimeZone.getTimeZone("America/Los_Angeles")

	fun prepareResult(title: String, startTime: Long, endTime: Long, timezone: String, allDay: Boolean): Cursor {
		return mock {
			on {getString(INDEX_TITLE)} doReturn title
			on {getString(INDEX_DESCRIPTION)} doReturn ""
			on {getString(INDEX_TIMEZONE)} doReturn timezone
			on {getLong(INDEX_BEGIN)} doReturn startTime
			on {getLong(INDEX_END)} doReturn endTime
			on {getString(INDEX_LOCATION)} doReturn "Location"
			on {getInt(INDEX_ALL_DAY)} doReturn if (allDay) 1 else 0
		}
	}

	@Test
	fun testConversionRegular() {
		val event = CalendarProvider.parseEvent(
				prepareResult("Title", 1637171100000, 1637196300000, "America/Los_Angeles", false)
		)
		event.start.timeZone = timeZone
		event.end.timeZone = timeZone
		assertEquals(321, event.start[Calendar.DAY_OF_YEAR])
		assertEquals(9, event.start[Calendar.HOUR_OF_DAY])
		assertEquals(45, event.start[Calendar.MINUTE])
		assertEquals(321, event.end[Calendar.DAY_OF_YEAR])
		assertEquals(16, event.end[Calendar.HOUR_OF_DAY])
		assertEquals(45, event.end[Calendar.MINUTE])

		val events = splitEventIntoDays(event)
		assertEquals(1, events.size)
		assertEquals(321, events[0].start[Calendar.DAY_OF_YEAR])
		assertEquals(9, events[0].start[Calendar.HOUR_OF_DAY])
		assertEquals(45, events[0].start[Calendar.MINUTE])
		assertEquals(321, events[0].end[Calendar.DAY_OF_YEAR])
		assertEquals(16, events[0].end[Calendar.HOUR_OF_DAY])
		assertEquals(45, events[0].end[Calendar.MINUTE])
	}

	@Test
	fun testConversionAllDay() {
		val event = CalendarProvider.parseEvent(
				prepareResult("Holiday", 1637798400000, 1637884800000, "UTC", true)
		)
		assertEquals(329, event.start[Calendar.DAY_OF_YEAR])
		assertEquals(0, event.start[Calendar.HOUR_OF_DAY])
		assertEquals(0, event.start[Calendar.MINUTE])
		assertEquals(330, event.end[Calendar.DAY_OF_YEAR])
		assertEquals(0, event.end[Calendar.HOUR_OF_DAY])
		assertEquals(0, event.end[Calendar.MINUTE])
	}

	@Test
	fun testConversionOtherTimezone() {
		val event = CalendarProvider.parseEvent(
				prepareResult("Appointment", 1637182200000, 1637183400000, "UTC", false)
		)
		event.start.timeZone = timeZone
		event.end.timeZone = timeZone
		assertEquals(321, event.start[Calendar.DAY_OF_YEAR])
		assertEquals(12, event.start[Calendar.HOUR_OF_DAY])
		assertEquals(50, event.start[Calendar.MINUTE])
		assertEquals(321, event.end[Calendar.DAY_OF_YEAR])
		assertEquals(13, event.end[Calendar.HOUR_OF_DAY])
		assertEquals(10, event.end[Calendar.MINUTE])
	}

	@Test
	fun testSliceByDays() {
		// specifically placed across DST boundary
		val event = CalendarProvider.parseEvent(
				prepareResult("Vacation", 1636130700000, 1636393500000, "America/Los_Angeles", false)
		)
		event.start.timeZone = timeZone
		event.end.timeZone = timeZone
		assertEquals(309, event.start[Calendar.DAY_OF_YEAR])
		assertEquals(9, event.start[Calendar.HOUR_OF_DAY])
		assertEquals(45, event.start[Calendar.MINUTE])
		assertEquals(312, event.end[Calendar.DAY_OF_YEAR])
		assertEquals(9, event.end[Calendar.HOUR_OF_DAY])
		assertEquals(45, event.end[Calendar.MINUTE])

		val events = splitEventIntoDays(event)
		assertEquals(4, events.size)
		assertEquals(309, events[0].start[Calendar.DAY_OF_YEAR])
		assertEquals(9, events[0].start[Calendar.HOUR_OF_DAY])
		assertEquals(45, events[0].start[Calendar.MINUTE])
		assertEquals(310, events[0].end[Calendar.DAY_OF_YEAR])
		assertEquals(0, events[0].end[Calendar.HOUR_OF_DAY])
		assertEquals(0, events[0].end[Calendar.MINUTE])
		assertEquals(310, events[1].start[Calendar.DAY_OF_YEAR])
		assertEquals(0, events[1].start[Calendar.HOUR_OF_DAY])
		assertEquals(0, events[1].start[Calendar.MINUTE])
		assertEquals(311, events[1].end[Calendar.DAY_OF_YEAR])
		assertEquals(0, events[1].end[Calendar.HOUR_OF_DAY])
		assertEquals(0, events[1].end[Calendar.MINUTE])
		assertEquals(311, events[2].start[Calendar.DAY_OF_YEAR])
		assertEquals(0, events[2].start[Calendar.HOUR_OF_DAY])
		assertEquals(0, events[2].start[Calendar.MINUTE])
		assertEquals(312, events[2].end[Calendar.DAY_OF_YEAR])
		assertEquals(0, events[2].end[Calendar.HOUR_OF_DAY])
		assertEquals(0, events[2].end[Calendar.MINUTE])
		assertEquals(312, events[3].start[Calendar.DAY_OF_YEAR])
		assertEquals(0, events[3].start[Calendar.HOUR_OF_DAY])
		assertEquals(0, events[3].start[Calendar.MINUTE])
		assertEquals(312, events[3].end[Calendar.DAY_OF_YEAR])
		assertEquals(9, events[3].end[Calendar.HOUR_OF_DAY])
		assertEquals(45, events[3].end[Calendar.MINUTE])
	}
}