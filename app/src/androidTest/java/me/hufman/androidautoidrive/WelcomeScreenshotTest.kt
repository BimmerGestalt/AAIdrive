package me.hufman.androidautoidrive

import androidx.test.annotation.UiThreadTest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.screenshot.Screenshot
import me.hufman.androidautoidrive.R.id.*
import me.hufman.androidautoidrive.phoneui.NavHostActivity
import me.hufman.androidautoidrive.phoneui.WelcomeActivity
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class WelcomeScreenshotTest {
	companion object {
		@JvmStatic
		@BeforeClass
		fun setUpClass() {
			val context = InstrumentationRegistry.getInstrumentation().targetContext
			AppSettings.saveSetting(context, AppSettings.KEYS.FIRST_START_DONE, "false")
		}
	}
	val context = InstrumentationRegistry.getInstrumentation().targetContext!!

	val mockScenario = MockScenario(context)

	// register the viewmodels when the test starts
	@get:Rule
	val mockViewModels = mockScenario.viewModels

	@get:Rule
	val activityScenario = activityScenarioRule<WelcomeActivity>()  // try to open main window, it should redirect to FirstStart

	@UiThreadTest
	@Before
	fun setUp() {
		println("Starting to update viewmodels")
		mockScenario.carCapabilitiesViewModel.update()
		mockScenario.connectionStatusModel.update()
		mockScenario.dependencyInfoModel.update()
	}

	val processor = PrivateScreenshotProcessor(context)
	fun screenshot(name: String) {
		activityScenario.scenario.onActivity {
			Screenshot.capture(it).apply {
				this.name = name
				process(setOf(processor))
			}
		}
	}

	@Test
	fun firstStartPages() {
		val expectedTabs = if (BuildConfig.FLAVOR_analytics == "nonalytics") {
			5
		} else {
			6
		}
		onView(withId(pgrWelcomeTabs)).check(matches(isCompletelyDisplayed()))
		(1..expectedTabs).forEach { page ->
			screenshot("welcome_$page")
			onView(withId(btnNext)).perform(click())
		}
		onView(withId(pgrWelcomeTabs)).check(doesNotExist())
		onView(withId(nav_host_fragment)).check(matches(isDisplayed()))
	}
}