package me.hufman.androidautoidrive.carapp.notifications

import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettingsObserver
import me.hufman.androidautoidrive.StoredList
import me.hufman.androidautoidrive.connections.BtStatus
import java.util.*

class NotificationSettings(val capabilities: Map<String, String?>, val btStatus: BtStatus, val appSettings: MutableAppSettingsObserver) {
	var callback
		get() = appSettings.callback
		set(value) { appSettings.callback = value }

	// quick replies for input suggestions
	val quickReplies: List<String> = StoredList(appSettings, AppSettings.KEYS.NOTIFICATIONS_QUICK_REPLIES)

	// car's supported features
	val tts = capabilities["tts"]?.lowercase(Locale.ROOT) == "true"

	fun getSettings(): List<AppSettings.KEYS> {
		val popupSettings = listOf(
				AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP,
				AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER
		)
		val soundSettings = listOf(
				AppSettings.KEYS.NOTIFICATIONS_SOUND
		)
		val readoutSettings = if (tts) {
			listOf(
					AppSettings.KEYS.NOTIFICATIONS_READOUT,
					AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP,
					AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER
			)
		} else {
			listOf()
		}
		return popupSettings + soundSettings + readoutSettings
	}

	fun shouldPopup(passengerSeated: Boolean): Boolean {
		return appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP].toBoolean() &&
			(appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER].toBoolean() ||
				!passengerSeated)
	}

	fun shouldPlaySound(): Boolean {
		return btStatus.isA2dpConnected && appSettings[AppSettings.KEYS.NOTIFICATIONS_SOUND].toBoolean()
	}

	fun shouldReadoutNotificationPopup(passengerSeated: Boolean): Boolean {
		val main = appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP].toBoolean()
		val passenger = appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER].toBoolean()
		return tts && main && (passenger || !passengerSeated)
	}

	fun shouldReadoutNotificationDetails(): Boolean {
		return tts && appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT].toBoolean()
	}
}