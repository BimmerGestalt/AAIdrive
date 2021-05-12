package me.hufman.androidautoidrive

import android.content.Context
import androidx.databinding.ObservableArrayList
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import androidx.test.annotation.UiThreadTest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.DrawerMatchers.isClosed
import androidx.test.espresso.contrib.DrawerMatchers.isOpen
import androidx.test.espresso.contrib.NavigationViewActions
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.screenshot.Screenshot
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.connections.CarConnectionDebugging
import me.hufman.androidautoidrive.music.MusicAppDiscovery
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicSessions
import me.hufman.androidautoidrive.phoneui.MusicAppDiscoveryThread
import me.hufman.androidautoidrive.phoneui.NavHostActivity
import me.hufman.androidautoidrive.phoneui.adapters.MusicAppListAdapter
import me.hufman.androidautoidrive.phoneui.fragments.MusicAppsListFragment
import me.hufman.androidautoidrive.phoneui.viewmodels.*
import org.junit.*
import org.mockito.stubbing.OngoingStubbing

// Helpers for mocking LiveData/Context values
infix fun <T> OngoingStubbing<LiveData<T>>.doReturn(value: T): OngoingStubbing<LiveData<T>> = thenReturn(MutableLiveData(value))
infix fun OngoingStubbing<BooleanLiveSetting>.doReturn(value: Boolean): OngoingStubbing<BooleanLiveSetting> = thenAnswer{ mock<BooleanLiveSetting> { on {getValue()} doReturn value } }
infix fun OngoingStubbing<StringLiveSetting>.doReturn(value: String): OngoingStubbing<StringLiveSetting> = thenAnswer{ mock<StringLiveSetting> { on {getValue()} doReturn value } }
infix fun <T> OngoingStubbing<LiveData<Context.() -> T>>.doReturnContexted(value: Context.() -> T): OngoingStubbing<LiveData<Context.() -> T>> = thenReturn(MutableLiveData(value))

class MainScreenshotTest {
	val context = InstrumentationRegistry.getInstrumentation().targetContext

	val connectionDebugging = mock<CarConnectionDebugging> {
		on {isBMWConnectedInstalled} doReturn true
		on {isConnectedSecurityInstalled} doReturn true
		on {isConnectedSecurityConnected} doReturn true
		on {isBCLConnected} doReturn true
		on {isBTConnected} doReturn true
		on {isSPPAvailable} doReturn true
		on {isA2dpConnected} doReturn true
		on {carBrand} doReturn "BMW"
	}
	val carInfo = mock<CarInformation> {
		on {capabilities} doReturn mapOf(
			"hmi.type" to "BMW ID5",
			"hmi.version" to "NBTevo_ID5_1903",
			"navi" to "true",
			"tts" to "true",
			"vehicle.type" to "F22"
		)
	}
	val musicAppMode = mock<MusicAppMode> {
		on {heuristicAudioContext()} doReturn true
		on {shouldId5Playback()} doReturn true
	}
	val carCapabilitiesViewModel = CarCapabilitiesViewModel(carInfo, musicAppMode)
	val connectionStatusModel = ConnectionStatusModel(connectionDebugging, carInfo)
	val dependencyInfoModel = DependencyInfoModel(connectionDebugging)

	val musicApps = ObservableArrayList<MusicAppInfo>().apply {
		add(MusicAppInfo("Green Player", context.getDrawable(R.drawable.ic_test_music_app2)!!, "mock", null).apply {
			connectable = true
			browseable = true
			searchable = true
		})
		add(MusicAppInfo("Music Player", context.getDrawable(R.drawable.ic_test_music_app1)!!, "mock", null).apply {
			controllable = true
		})
		add(MusicAppInfo("Unsupported Player", context.getDrawable(R.drawable.ic_test_music_app1)!!, "mock", null).apply {
			connectable = false
			hidden = true
		})
	}
	val musicAppDiscovery = mock<MusicAppDiscovery> {
		on {musicSessions} doReturn mock<MusicSessions>()
	}
	val musicAppDiscoveryThread = mock<MusicAppDiscoveryThread> {
		on {discovery} doReturn musicAppDiscovery
	}
	val musicAppsModel = mock<MusicAppsViewModel> {
		on {validApps} doReturn ObservableArrayList<MusicAppInfo>().apply {addAll(musicApps.filter { it.connectable || it.controllable})}
		on {allApps} doReturn musicApps
		on {musicAppDiscoveryThread} doReturn musicAppDiscoveryThread
	}
	val permissionsModel = mock<PermissionsModel> {
		on {hasSpotify} doReturn true
		on {hasSpotifyControlPermission} doReturn true
		on {isSpotifyWebApiAuthorized} doReturn true
		on {hasNotificationPermission} doReturn true
		on {hasSmsPermission} doReturn true
		on {hasLocationPermission} doReturn true
	}

	val mapSettingsModel = mock<MapSettingsModel> {
		on {mapEnabled} doReturn true
		on {mapStyle} doReturn ""
		on {mapWidescreen} doReturn true
		on {mapTraffic} doReturn true
	}

	val navigationStatusModel = mock<NavigationStatusModel> {
		on {isConnected} doReturn true
		on {navigationStatus} doReturnContexted {getString(R.string.lbl_navigationstatus_inactive)}
	}

	val notificationSettingsModel = mock<NotificationSettingsModel> {
		on {notificationEnabled} doReturn true
		on {notificationPopup} doReturn false
		on {notificationPopupPassenger} doReturn false
		on {notificationReadout} doReturn false
		on {notificationReadoutPopupPassenger} doReturn false
		on {notificationSound} doReturn false
	}

	@get:Rule
	val mockViewModels = MockViewModels().also {
		it[CarCapabilitiesViewModel::class.java] = carCapabilitiesViewModel
		it[ConnectionStatusModel::class.java] = connectionStatusModel
		it[DependencyInfoModel::class.java] = dependencyInfoModel
		it[MapSettingsModel::class.java] = mapSettingsModel
		it[MusicAppsViewModel::class.java] = musicAppsModel
		it[NavigationStatusModel::class.java] = navigationStatusModel
		it[NotificationSettingsModel::class.java] = notificationSettingsModel
		it[PermissionsModel::class.java] = permissionsModel
	}

	@UiThreadTest
	@Before
	fun setUp() {
		println("Starting to update viewmodels")
		carCapabilitiesViewModel.update()
		connectionStatusModel.update()
		dependencyInfoModel.update()
	}

	@get:Rule
	val activityScenario = activityScenarioRule<NavHostActivity>()

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
		whenever(connectionDebugging.isBCLConnected) doReturn false
		whenever(connectionDebugging.carBrand) doReturn null
		activityScenario.scenario.onActivity {
			connectionStatusModel.update()
		}

		Thread.sleep(1000)
		onView(withId(R.id.drawer_layout)).check(matches(isOpen()))
		screenshot("disconnected_sidebar")
		onView(withId(R.id.drawer_layout)).perform(DrawerActions.close())
		screenshot("disconnect_home")
		onView(withId(R.id.drawer_layout)).check(matches(isClosed()))
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
	fun mapsTab() {
		onView(withId(R.id.nav_view)).perform(NavigationViewActions
				.navigateTo(
						R.id.nav_maps
				))

		screenshot("maps")
	}

	@Test
	fun musicTab() {
		onView(withId(R.id.nav_view)).perform(NavigationViewActions
				.navigateTo(
						R.id.nav_music
				))

		screenshot("music")

		// open the music app features
		onView(withId(R.id.listMusicApps))
				.perform(RecyclerViewActions.actionOnItemAtPosition<MusicAppListAdapter.ViewHolder>(0,
				ClickChildView.clickChildViewWithId(R.id.paneMusicAppFeatures)))
		onView(withId(R.id.listMusicApps))
				.perform(RecyclerViewActions.actionOnItemAtPosition<MusicAppListAdapter.ViewHolder>(1,
				ClickChildView.clickChildViewWithId(R.id.paneMusicAppFeatures)))
		onView(withId(R.id.listMusicApps))
				.perform(RecyclerViewActions.actionOnItemAtPosition<MusicAppListAdapter.ViewHolder>(2,
				ClickChildView.clickChildViewWithId(R.id.paneMusicAppFeatures)))

		screenshot("music_expanded")
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
	fun notificationsTab() {
		onView(withId(R.id.nav_view)).perform(NavigationViewActions
				.navigateTo(
						R.id.nav_notifications
				))

		screenshot("notifications")
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