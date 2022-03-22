package me.hufman.androidautoidrive

import androidx.test.annotation.UiThreadTest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.DrawerMatchers.isClosed
import androidx.test.espresso.contrib.DrawerMatchers.isOpen
import androidx.test.espresso.contrib.NavigationViewActions
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.screenshot.Screenshot
import androidx.viewpager2.widget.ViewPager2
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import me.hufman.androidautoidrive.EspressoHelpers.withCustomConstraints
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.phoneui.NavHostActivity
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundViewHolder
import me.hufman.androidautoidrive.phoneui.controllers.MusicAppListController
import me.hufman.androidautoidrive.phoneui.viewmodels.TipsModel
import org.junit.*

class MainScreenshotTest {
	companion object {
		@JvmStatic
		@BeforeClass
		fun setUpClass() {
			val context = InstrumentationRegistry.getInstrumentation().targetContext
			AppSettings.saveSetting(context, AppSettings.KEYS.FIRST_START_DONE, "true")
		}
	}

	val context = InstrumentationRegistry.getInstrumentation().targetContext

	// holds all the mock viewmodels
	val mockScenario = MockScenario(context)

	// register the viewmodels when the test starts
	@get:Rule
	val mockViewModels = mockScenario.viewModels

	@get:Rule
	val activityScenario = activityScenarioRule<NavHostActivity>()

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

	fun screenshotTips(name: String) {
		onView(withId(R.id.pane_tiplist_expand)).perform(scrollTo())
		onView(withId(R.id.pane_tiplist_expand)).perform(click())
		var count = 0
		activityScenario.scenario.onActivity { activity ->
			count = activity.findViewById<ViewPager2>(R.id.pgrTipsList).adapter?.itemCount ?: 1
		}
		(1..count).forEach {
			screenshot("${name}_$it")
			onView(withId(R.id.pgrTipsList)).perform(
					withCustomConstraints(swipeLeft(), isDisplayingAtLeast(75))
			)
		}
		onView(withId(R.id.pane_tiplist_expand)).perform(scrollTo())
		onView(withId(R.id.pane_tiplist_expand)).perform(click())
	}

	@Test
	fun homeScreenshot() {
		Thread.sleep(1000)
		onView(withId(R.id.drawer_layout)).check(matches(isOpen()))
		screenshot("home_sidebar")
		onView(withId(R.id.drawer_layout)).perform(DrawerActions.close())
		screenshot("home")
		onView(withId(R.id.drawer_layout)).check(matches(isClosed()))
	}

	@Test
	fun disconnectedScreenshot() {
		whenever(mockScenario.connectionDebugging.isBCLConnected) doReturn false
		whenever(mockScenario.connectionDebugging.carBrand) doReturn null
		activityScenario.scenario.onActivity {
			mockScenario.connectionStatusModel.update()
		}

		Thread.sleep(1000)
		onView(withId(R.id.drawer_layout)).check(matches(isOpen()))
		screenshot("disconnected_sidebar")
		onView(withId(R.id.drawer_layout)).perform(DrawerActions.close())
		screenshot("disconnect_home")
		onView(withId(R.id.drawer_layout)).check(matches(isClosed()))

		screenshotTips("connection_tips")
	}

	@Test
	fun connectionTab() {
		onView(withId(R.id.nav_view)).perform(NavigationViewActions
			.navigateTo(
				R.id.nav_connection
			))

		screenshot("connection")
	}

	@Test
	fun assistantsTab() {
		onView(withId(R.id.nav_view)).perform(NavigationViewActions
				.navigateTo(
						R.id.nav_assistants
				))

		screenshot("assistants")
	}

	@Test
	fun calendarTab() {
		onView(withId(R.id.nav_view)).perform(NavigationViewActions
				.navigateTo(
						R.id.nav_calendar
				))

		screenshot("calendar")
	}

	@Test
	fun mapsTab() {
		onView(withId(R.id.nav_view)).perform(NavigationViewActions
				.navigateTo(
						R.id.nav_maps
				))

		screenshot("maps")
	}

	@Test
	fun musicBmwTab() {
		mockViewModels[TipsModel::class.java] = mockScenario.capabilitiesTipsModel
		onView(withId(R.id.nav_view)).perform(NavigationViewActions
				.navigateTo(
						R.id.nav_music
				))

		screenshot("music")

		screenshotTips("music_tips_bmw")

		// open the music app features
		onView(withId(R.id.listMusicApps))
				.perform(RecyclerViewActions.actionOnItemAtPosition<DataBoundViewHolder<MusicAppInfo, MusicAppListController>>(0,
				EspressoHelpers.clickChildViewWithId(R.id.paneMusicAppFeatures)))
		onView(withId(R.id.listMusicApps))
				.perform(RecyclerViewActions.actionOnItemAtPosition<DataBoundViewHolder<MusicAppInfo, MusicAppListController>>(1,
				EspressoHelpers.clickChildViewWithId(R.id.paneMusicAppFeatures)))
		onView(withId(R.id.listMusicApps))
				.perform(RecyclerViewActions.actionOnItemAtPosition<DataBoundViewHolder<MusicAppInfo, MusicAppListController>>(2,
				EspressoHelpers.clickChildViewWithId(R.id.paneMusicAppFeatures)))

		screenshot("music_expanded")
	}

	@Test
	fun musicMiniTab() {
		whenever(mockScenario.carInfo.capabilities) doReturn mapOf(
				"hmi.type" to "MINI ID5",
				"hmi.version" to "NBTevo_ID5_1903",
				"navi" to "true",
				"tts" to "true",
				"vehicle.type" to "F56"
		)
		mockViewModels[TipsModel::class.java] = mockScenario.capabilitiesTipsModel
		onView(withId(R.id.nav_view)).perform(NavigationViewActions
				.navigateTo(
						R.id.nav_music
				))

		screenshot("music")

		screenshotTips("music_tips_mini")
	}

	@Test
	fun navigationTab() {
		onView(withId(R.id.nav_view)).perform(NavigationViewActions
				.navigateTo(
						R.id.nav_navigation
				))

		screenshot("navigation")
	}

	@Test
	fun notificationsBmwTab() {
		mockViewModels[TipsModel::class.java] = mockScenario.capabilitiesTipsModel
		onView(withId(R.id.nav_view)).perform(NavigationViewActions
				.navigateTo(
						R.id.nav_notifications
				))

		screenshot("notifications")

		screenshotTips("notification_tips_bmw")
	}
	@Test
	fun notificationsMiniTab() {
		whenever(mockScenario.carInfo.capabilities) doReturn mapOf(
				"hmi.type" to "MINI ID5",
				"hmi.version" to "NBTevo_ID5_1903",
				"navi" to "true",
				"tts" to "true",
				"vehicle.type" to "F56"
		)
		mockViewModels[TipsModel::class.java] = mockScenario.capabilitiesTipsModel
		onView(withId(R.id.nav_view)).perform(NavigationViewActions
				.navigateTo(
						R.id.nav_notifications
				))

		screenshot("notifications")

		screenshotTips("notification_tips_mini")
	}

	@Test
	fun settingsTab() {
		onView(withId(R.id.nav_view)).perform(NavigationViewActions
				.navigateTo(
						R.id.nav_settings
				))

		screenshot("settings")
	}

	@Test
	fun supportTab() {
		onView(withId(R.id.nav_view)).perform(NavigationViewActions
				.navigateTo(
						R.id.nav_support
				))

		screenshot("support")
	}
}