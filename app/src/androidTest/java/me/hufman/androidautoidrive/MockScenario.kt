package me.hufman.androidautoidrive

import android.content.Context
import androidx.databinding.ObservableArrayList
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import me.hufman.androidautoidrive.calendar.CalendarEvent
import me.hufman.androidautoidrive.calendar.PhoneCalendar
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.connections.CarConnectionDebugging
import me.hufman.androidautoidrive.music.MusicAppDiscovery
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicSessions
import me.hufman.androidautoidrive.phoneui.MusicAppDiscoveryThread
import me.hufman.androidautoidrive.phoneui.viewmodels.*
import org.mockito.Mockito
import org.mockito.stubbing.Answer
import org.mockito.stubbing.OngoingStubbing
import java.util.*
import kotlin.collections.ArrayList

// Helpers for mocking LiveData/Context values
infix fun <T> OngoingStubbing<LiveData<T>>.doReturn(value: T): OngoingStubbing<LiveData<T>> = thenReturn(MutableLiveData(value))
infix fun OngoingStubbing<BooleanLiveSetting>.doReturn(value: Boolean): OngoingStubbing<BooleanLiveSetting> = thenAnswer{ mock<BooleanLiveSetting> { on {getValue()} doReturn value } }
infix fun OngoingStubbing<StringLiveSetting>.doReturn(value: String): OngoingStubbing<StringLiveSetting> = thenAnswer{ mock<StringLiveSetting> { on {getValue()} doReturn value } }
infix fun <T> OngoingStubbing<LiveData<Context.() -> T>>.doReturnContexted(value: Context.() -> T): OngoingStubbing<LiveData<Context.() -> T>> = thenReturn(MutableLiveData(value))
infix fun <T> OngoingStubbing<MutableLiveData<Context.() -> T>>.doReturnMutableContexted(value: Context.() -> T): OngoingStubbing<MutableLiveData<Context.() -> T>> = thenReturn(MutableLiveData(value))

class MockScenario(context: Context) {
	val connectionDebugging = mock<CarConnectionDebugging> {
		on {isBMWConnectedInstalled} doReturn false
		on {isBMWMineInstalled} doReturn true
		on {isBMWInstalled} doReturn true
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
	val dependencyInfoModel = DependencyInfoModel(connectionDebugging, mock())

	val connectionTipsModel = ConnectionTipsModel(connectionDebugging)
	val capabilitiesTipsModel = CapabilitiesTipsModel(carInfo)

	val calendarSettingsModel = mock<CalendarSettingsModel> {
		on {calendarEnabled} doReturn true
		on {areCalendarsFound} doReturn true
		on {autonav} doReturn true
	}
	val calendarEventsModel = mock<CalendarEventsModel> {
		on {calendars} doReturn ArrayList(listOf(
				PhoneCalendar("Holidays", false, 0xcff9eb),
				PhoneCalendar("Personal", true, 0xdbe6fd),
		))
		on {upcomingEvents} doReturn ArrayList(listOf(
				CalendarEvent("Dinner", Calendar.getInstance().also {
						it[Calendar.MONTH] = 2
						it[Calendar.DAY_OF_MONTH] = 19
						it[Calendar.HOUR_OF_DAY] = 18
						it[Calendar.MINUTE] = 0
					}, Calendar.getInstance().also {
						it[Calendar.MONTH] = 2
						it[Calendar.DAY_OF_MONTH] = 19
						it[Calendar.HOUR_OF_DAY] = 20
						it[Calendar.MINUTE] = 0
					}, "", "", 0xdbe6fd)
		))
	}

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
		on {hasCalendarPermission} doReturn true
		on {hasNotificationPermission} doReturn true
		on {hasSmsPermission} doReturn true
		on {hasLocationPermission} doReturn true
		on {hasBackgroundPermission} doReturn true
	}

	// https://stackoverflow.com/a/60119030/169035
	val nonNullStringAnswer = Answer { invocation ->
		// println("Returning default answer for $invocation with type ${invocation.method.returnType}")
		when (invocation.method.returnType) {
			String::class.java -> ""
			StringLiveSetting::class.java -> mock<StringLiveSetting> { on { value } doReturn "" }
			else -> Mockito.RETURNS_DEFAULTS.answer(invocation)
		}
	}

	val mapSettingsModel = mock<MapSettingsModel>(defaultAnswer=nonNullStringAnswer) {
		on {mapEnabled} doReturn true
		on {mapPhoneGps} doReturn false
		on {mapWidescreen} doReturn true
	}

	val navigationStatusModel = mock<NavigationStatusModel> {
		on {isConnected} doReturn true
		on {query} doAnswer {MutableLiveData("")}
		on {carNavigationStatus} doReturnContexted {getString(R.string.lbl_navigationstatus_inactive)}
		on {searchStatus} doReturnMutableContexted {getString(R.string.lbl_navigation_listener_pending)}
		on {searchFailed} doAnswer {MutableLiveData(false)}
	}

	val notificationSettingsModel = mock<NotificationSettingsModel> {
		on {notificationEnabled} doReturn true
		on {notificationPopup} doReturn false
		on {notificationPopupPassenger} doReturn false
		on {notificationReadout} doReturn false
		on {notificationReadoutPopupPassenger} doReturn false
		on {notificationSound} doReturn false
	}

	val languageSettingsModel = mock<LanguageSettingsModel> {
		on { lblPreferCarLanguage } doReturnContexted { getString(R.string.lbl_language_prefercar) }
		on { showAdvanced } doReturn true
		on { preferCarLanguage } doReturn true
		on { forceCarLanguage} doReturn ""
	}

	val viewModels = MockViewModels().also {
		it[CarCapabilitiesViewModel::class.java] = carCapabilitiesViewModel
		it[CalendarSettingsModel::class.java] = calendarSettingsModel
		it[CalendarEventsModel::class.java] = calendarEventsModel
		it[ConnectionStatusModel::class.java] = connectionStatusModel
		it[DependencyInfoModel::class.java] = dependencyInfoModel
		it[LanguageSettingsModel::class.java] = languageSettingsModel
		it[MapSettingsModel::class.java] = mapSettingsModel
		it[MusicAppsViewModel::class.java] = musicAppsModel
		it[NavigationStatusModel::class.java] = navigationStatusModel
		it[NotificationSettingsModel::class.java] = notificationSettingsModel
		it[PermissionsModel::class.java] = permissionsModel
		it[TipsModel::class.java] = connectionTipsModel     // set the initial TipsModel for the main tab
	}
}