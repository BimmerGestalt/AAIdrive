package me.hufman.androidautoidrive.calendar

import me.hufman.androidautoidrive.carapp.calendar.RHMIDateUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

class RHMIDateUtilsTest {
	@Test
	fun testRhmiDateConversion() {
		val start = Calendar.getInstance().apply {
			set(2021, 3, 6)
		}
		val middle = RHMIDateUtils.convertToRhmiDate(start)
		assertEquals(132449286, middle)
		val end = RHMIDateUtils.convertFromRhmiDate(middle)
		assertEquals(start[Calendar.YEAR], end[Calendar.YEAR])
		assertEquals(start[Calendar.MONTH], end[Calendar.MONTH])
		assertEquals(start[Calendar.DAY_OF_MONTH], end[Calendar.DAY_OF_MONTH])
	}

	@Test
	fun testRhmiTimeConversion() {
		val start = Calendar.getInstance().apply {
			set(2021, 3, 6)
			set(Calendar.HOUR_OF_DAY, 14)
			set(Calendar.MINUTE, 16)
			set(Calendar.SECOND, 18)
			set(Calendar.MILLISECOND, 0)
		}
		val carTime = RHMIDateUtils.convertToRhmiTime(start)
		assertEquals(1183758, carTime)
	}
}