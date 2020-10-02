package me.hufman.androidautoidrive

import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.carapp.notifications.NotificationSettings
import me.hufman.androidautoidrive.connections.BtStatus
import org.junit.Assert.*
import org.junit.Test

class TestNotificationSettings {

	val btStatus = mock<BtStatus> {
		on { isA2dpConnected } doReturn true
	}
	val appSettings = mock<MutableAppSettings> {
		val captor = argumentCaptor<AppSettings.KEYS>()
		on { get(captor.capture()) } doAnswer {AppSettings[captor.lastValue]}
	}

	@Test
	fun testCapabilities() {
		run {
			val settings = NotificationSettings(mapOf("hmi.type" to "MINI ID4++", "tts" to "true"), btStatus, appSettings)
			assertEquals(listOf(
					AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP, AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER, AppSettings.KEYS.NOTIFICATIONS_SOUND,
					AppSettings.KEYS.NOTIFICATIONS_READOUT, AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP, AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER
			), settings.getSettings())
		}

		run {
			val settings = NotificationSettings(mapOf("hmi.type" to "MINI ID5", "tts" to "true"), btStatus, appSettings)
			assertEquals(listOf(AppSettings.KEYS.NOTIFICATIONS_SOUND,
					AppSettings.KEYS.NOTIFICATIONS_READOUT, AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP, AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER
			), settings.getSettings())
		}

		run {
			val settings = NotificationSettings(mapOf("hmi.type" to "MINI ID4++", "tts" to "false"), btStatus, appSettings)
			assertEquals(listOf(
					AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP, AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER,
					AppSettings.KEYS.NOTIFICATIONS_SOUND
			), settings.getSettings())
		}

		run {
			val settings = NotificationSettings(mapOf("hmi.type" to "MINI ID5", "tts" to "false"), btStatus, appSettings)
			assertEquals(listOf(AppSettings.KEYS.NOTIFICATIONS_SOUND), settings.getSettings())
		}
	}

	@Test
	fun testSettings() {
		whenever(appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP]) doReturn "true"
		whenever(appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER]) doReturn "false"
		whenever(appSettings[AppSettings.KEYS.NOTIFICATIONS_SOUND]) doReturn "true"
		whenever(appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT]) doReturn "true"
		whenever(appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP]) doReturn "true"
		whenever(appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER]) doReturn "false"
		val settings = NotificationSettings(mapOf("hmi.type" to "MINI ID4++", "tts" to "true"), btStatus, appSettings)
		assertTrue(settings.shouldPopup(false))
		assertFalse(settings.shouldPopup(true))
		assertTrue(settings.shouldPlaySound())
		assertTrue(settings.shouldReadoutNotificationDetails())
		assertTrue(settings.shouldReadoutNotificationPopup(false))
		assertFalse(settings.shouldReadoutNotificationPopup(true))
	}

	@Test
	fun testCallback() {
		var callbackRan = false
		val appSettingsCallback = argumentCaptor<() -> Unit>()

		val settings = NotificationSettings(mapOf("hmi.type" to "MINI ID4++", "tts" to "true"), btStatus, appSettings)
		settings.callback = { callbackRan = true }
		verify(appSettings).callback = appSettingsCallback.capture()
		appSettingsCallback.lastValue()
		assertTrue(callbackRan)
	}

	@Test
	fun testToggle() {
		val settings = NotificationSettings(mapOf("hmi.type" to "MINI ID4++", "tts" to "true"), btStatus, appSettings)
		assertFalse(settings.isChecked(AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER))
		verify(appSettings).get(AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER)

		settings.toggleSetting(AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER)
		verify(appSettings)[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER] = "true"
	}
}