package me.hufman.androidautoidrive.carapp.notifications

import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettings

class NotificationSettings(val capabilities: Map<String, String>, val appSettings: MutableAppSettings) {
	var callback
		get() = appSettings.callback
		set(value) { appSettings.callback = value }

	fun getSettings(): List<AppSettings.KEYS> {
		val idrive4 = capabilities["hmi.type"]?.contains("ID4") == true
		val settings = if (idrive4) {
			listOf(
					AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP,
					AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER
			)
		} else {
			listOf()
		}
		return settings
	}

	fun toggleSetting(setting: AppSettings.KEYS) {
		appSettings[setting] = (!appSettings[setting].toBoolean()).toString()
	}

	fun isChecked(setting: AppSettings.KEYS): Boolean {
		return appSettings[setting].toBoolean()
	}
}