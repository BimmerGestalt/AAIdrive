package me.hufman.androidautoidrive

import me.hufman.androidautoidrive.phoneui.DayCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DonationRequestTest {
	class DayIncremented {
		var days = 0
		fun onDayIncremented() {
			days += 1
		}
	}

	@Test
	fun testFirstDay() {
		val appSettings = MockAppSettings(
				AppSettings.KEYS.DONATION_LAST_DAY to "2000-01-01",
				AppSettings.KEYS.DONATION_DAYS_COUNT to "0")
		val dayIncremented = DayIncremented()
		val dayCounter = DayCounter(appSettings) { dayIncremented.onDayIncremented() }
		assertEquals(0, dayCounter.daysCounted())
		dayCounter.countUsage()
		assertNotEquals("2000-01-01", appSettings[AppSettings.KEYS.DONATION_LAST_DAY])
		assertEquals("1", appSettings[AppSettings.KEYS.DONATION_DAYS_COUNT])

		assertEquals(1, dayIncremented.days)
	}

	@Test
	fun testSameDay() {
		val appSettings = MockAppSettings(
				AppSettings.KEYS.DONATION_LAST_DAY to "2000-01-01",
				AppSettings.KEYS.DONATION_DAYS_COUNT to "0")
		val dayIncremented = DayIncremented()
		val dayCounter = DayCounter(appSettings) { dayIncremented.onDayIncremented() }
		assertEquals(0, dayCounter.daysCounted())
		dayCounter.countUsage()

		assertEquals("1", appSettings[AppSettings.KEYS.DONATION_DAYS_COUNT])
		assertEquals(1, dayIncremented.days)

		// the same day shouldn't increment the Days
		dayCounter.countUsage() // again on the same day
		assertEquals("1", appSettings[AppSettings.KEYS.DONATION_DAYS_COUNT])
		assertEquals(1, dayIncremented.days)
	}
}