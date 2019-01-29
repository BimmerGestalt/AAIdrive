package me.hufman.androidautoidrive

import me.hufman.androidautoidrive.carapp.maps.LatLong
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.*

class TestUtils {
	@Test
	fun testDayMode() {
		val now = Calendar.getInstance(TimeZone.getTimeZone("GMT-8"))
		now.set(2019, 1, 1, 14, 0)
		val location = LatLong(37.333, -121.9)
		assertTrue("2pm is day", TimeUtils.getDayMode(location, now))
		now.set(Calendar.HOUR_OF_DAY, 20)
		assertFalse("8pm is night", TimeUtils.getDayMode(location, now))
	}
}