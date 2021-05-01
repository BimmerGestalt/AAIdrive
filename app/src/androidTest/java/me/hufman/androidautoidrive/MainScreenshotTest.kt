package me.hufman.androidautoidrive

import androidx.activity.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.DrawerMatchers.isOpen
import androidx.test.espresso.contrib.NavigationViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.screenshot.Screenshot
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import me.hufman.androidautoidrive.phoneui.NavHostActivity
import me.hufman.androidautoidrive.phoneui.viewmodels.ConnectionStatusModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test


class MainScreenshotTest {
	val context = InstrumentationRegistry.getInstrumentation().targetContext
	val processor = PrivateScreenshotProcessor(context)

	@get:Rule
	val activityScenario = activityScenarioRule<NavHostActivity>()

	val connectionStatusModel = mock<ConnectionStatusModel> {
		on {isBclConnected} doAnswer { MutableLiveData(true) }
	}
	val factory = mock<ViewModelProvider.Factory> {
		on { create(ConnectionStatusModel::class.java) } doReturn connectionStatusModel
	}

	@Before
	fun setUp() {
		activityScenario.scenario.onActivity {
			it.viewModels<ConnectionStatusModel> { factory }
		}
	}
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