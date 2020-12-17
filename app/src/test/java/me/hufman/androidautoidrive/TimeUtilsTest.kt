package me.hufman.androidautoidrive

import me.hufman.androidautoidrive.carapp.maps.LatLong
import me.hufman.androidautoidrive.utils.TimeUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class TimeUtilsTest {
	@Test
	fun testDayMode() {
		val now = Calendar.getInstance(TimeZone.getTimeZone("GMT-8"))
		now.set(2019, 1, 1, 14, 0)
		val location = LatLong(37.333, -121.9)
		assertTrue("2pm is day", TimeUtils.getDayMode(location, now))
		now.set(Calendar.HOUR_OF_DAY, 20)
		assertFalse("8pm is night", TimeUtils.getDayMode(location, now))
	}

	@Test
	fun testFormatTime() {
		assertEquals(" --:--", TimeUtils.formatTime(-1))
		assertEquals("  0:09", TimeUtils.formatTime(9200))
		assertEquals("  0:59", TimeUtils.formatTime(59200))
		assertEquals("  1:39", TimeUtils.formatTime(99200))
		assertEquals("999:59", TimeUtils.formatTime((999*60 + 59)*1000))
		assertEquals("1000:00", TimeUtils.formatTime((999*60 + 60)*1000))
	}
}