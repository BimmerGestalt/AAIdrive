package me.hufman.androidautoidrive

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.DrawerMatchers.isOpen
import androidx.test.espresso.contrib.NavigationViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.screenshot.Screenshot
import me.hufman.androidautoidrive.phoneui.NavHostActivity
import org.junit.Rule
import org.junit.Test
import java.sql.Connection


class MainScreenshotTest {
	val context = InstrumentationRegistry.getInstrumentation().targetContext
	val processor = PrivateScreenshotProcessor(context)

	@get:Rule
	val activityScenario = activityScenarioRule<NavHostActivity>()

	fun screenshot(name: String) {
		activityScenario.scenario.onActivity {
			Screenshot.capture(it).apply {
				this.name = name
				process(setOf(processor))
			}
		}
	}

	@Test
	fun homeScreenshot() {
		onView(withId(R.id.drawer_layout)).check(matches(isOpen()))
		screenshot("home_sidebar")
		onView(withId(R.id.drawer_layout)).perform(DrawerActions.close())
		screenshot("home")
	}

	@Test
	fun connection2() {
		onView(withId(R.id.nav_view)).perform(NavigationViewActions
			.navigateTo(
				R.id.nav_connection
			))

		screenshot("connection")
	}
}