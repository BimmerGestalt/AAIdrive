package me.hufman.androidautoidrive

import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.phoneui.DayCounter
import org.junit.Assert.assertEquals
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
		val appSettings = mock<MutableAppSettings> {
			on { get(AppSettings.KEYS.DONATION_LAST_DAY) } doReturn "2000-01-01"
			on { get(AppSettings.KEYS.DONATION_DAYS_COUNT) } doReturn "0"
		}
		val dayIncremented = DayIncremented()
		val dayCounter = DayCounter(appSettings) { dayIncremented.onDayIncremented() }
		assertEquals(0, dayCounter.daysCounted())
		dayCounter.countUsage()
		verify(appSettings)[eq(AppSettings.KEYS.DONATION_LAST_DAY)] = any()
		verify(appSettings)[eq(AppSettings.KEYS.DONATION_DAYS_COUNT)] = eq("1")

		assertEquals(1, dayIncremented.days)
	}

	@Test
	fun testSameDay() {
		val appSettings = mock<MutableAppSettings> {
			on { get(AppSettings.KEYS.DONATION_LAST_DAY) } doReturn "2000-01-01"
			on { get(AppSettings.KEYS.DONATION_DAYS_COUNT) } doReturn "0"
		}
		val dayIncremented = DayIncremented()
		val dayCounter = DayCounter(appSettings) { dayIncremented.onDayIncremented() }
		assertEquals(0, dayCounter.daysCounted())
		dayCounter.countUsage()

		argumentCaptor<String>().apply {
			verify(appSettings)[eq(AppSettings.KEYS.DONATION_LAST_DAY)] = capture()
			verify(appSettings)[eq(AppSettings.KEYS.DONATION_DAYS_COUNT)] = eq("1")

			whenever(appSettings[AppSettings.KEYS.DONATION_LAST_DAY]) doReturn firstValue
			whenever(appSettings[AppSettings.KEYS.DONATION_DAYS_COUNT]) doReturn "1"
		}
		assertEquals(1, dayIncremented.days)

		// the same day shouldn't increment the Days
		dayCounter.countUsage() // again on the same day
		verify(appSettings)[eq(AppSettings.KEYS.DONATION_LAST_DAY)] = any()
		verify(appSettings)[eq(AppSettings.KEYS.DONATION_DAYS_COUNT)] = eq("1")
		assertEquals(1, dayIncremented.days)
	}
}