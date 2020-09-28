package me.hufman.androidautoidrive.carapp.notifications

import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettings

class NotificationSettings(val capabilities: Map<String, String>, val appSettings: MutableAppSettings) {
	var callback
		get() = appSettings.callback
		set(value) { appSettings.callback = value }

	fun getSettings(): List<AppSettings.KEYS> {
		val idrive4 = capabilities["hmi.type"]?.contains("ID4") == true
		val popupSettings = if (idrive4) {
			listOf(
					AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP,
					AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER
			)
		} else {
			listOf()
		}
		val readoutSettings = listOf(
				AppSettings.KEYS.NOTIFICATIONS_READOUT,
				AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP,
				AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER
		)
		return popupSettings + readoutSettings
	}

	fun toggleSetting(setting: AppSettings.KEYS) {
		appSettings[setting] = (!appSettings[setting].toBoolean()).toString()
	}

	fun isChecked(setting: AppSettings.KEYS): Boolean {
		return appSettings[setting].toBoolean()
	}
}