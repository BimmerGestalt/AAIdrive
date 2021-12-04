package me.hufman.androidautoidrive

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.hufman.androidautoidrive.calendar.CalendarProvider
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarTest {
	@Test
	@Ignore("Debugging")
	fun testCalendar() {
		val appContext = InstrumentationRegistry.getInstrumentation().targetContext
		val calendar = CalendarProvider(appContext, AppSettingsViewer())
		calendar.getEvents(2021, 12, 31)
	}
}