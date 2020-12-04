package me.hufman.androidautoidrive.carapp.notifications

import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettingsObserver
import me.hufman.androidautoidrive.connections.BtStatus

class NotificationSettings(val capabilities: Map<String, String?>, val btStatus: BtStatus, val appSettings: MutableAppSettingsObserver) {
	var callback
		get() = appSettings.callback
		set(value) { appSettings.callback = value }

	// car's supported features
	val idrive4 = capabilities["hmi.type"]?.contains("ID4") == true
	val tts = capabilities["tts"]?.toLowerCase() == "true"

	fun getSettings(): List<AppSettings.KEYS> {
		val popupSettings = if (idrive4) {
			listOf(
					AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP,
					AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER
			)
		} else {
			listOf()
		}
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

	fun toggleSetting(setting: AppSettings.KEYS) {
		appSettings[setting] = (!appSettings[setting].toBoolean()).toString()
	}

	fun isChecked(setting: AppSettings.KEYS): Boolean {
		return appSettings[setting].toBoolean()
	}

	fun shouldPopup(passengerSeated: Boolean): Boolean {
		return idrive4 &&
			appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP].toBoolean() &&
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