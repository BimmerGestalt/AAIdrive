package me.hufman.androidautoidrive.notifications

import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MockAppSettings
import me.hufman.androidautoidrive.carapp.notifications.NotificationSettings
import me.hufman.androidautoidrive.connections.BtStatus
import org.junit.Assert.*
import org.junit.Test

class NotificationSettingsTest {

	val btStatus = mock<BtStatus> {
		on { isA2dpConnected } doReturn true
	}
	val appSettings = MockAppSettings()

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
			assertEquals(listOf(AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP, AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER, AppSettings.KEYS.NOTIFICATIONS_SOUND,
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
			assertEquals(listOf(AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP, AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER,
					AppSettings.KEYS.NOTIFICATIONS_SOUND), settings.getSettings())
		}
	}

	@Test
	fun testSettings() {
		appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP] = "true"
		appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER] = "false"
		appSettings[AppSettings.KEYS.NOTIFICATIONS_SOUND] = "true"
		appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT] = "true"
		appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP] = "true"
		appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER] = "false"
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

		val settings = NotificationSettings(mapOf("hmi.type" to "MINI ID4++", "tts" to "true"), btStatus, appSettings)
		settings.callback = { callbackRan = true }
		appSettings.callback?.invoke()
		assertTrue(callbackRan)
	}
}