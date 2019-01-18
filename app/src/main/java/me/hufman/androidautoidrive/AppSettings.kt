package me.hufman.androidautoidrive

import android.content.Context
import android.content.Context.MODE_PRIVATE

object AppSettings {
	class SettingDefinition (val name: String, val default: String, val comment: String)

	private const val PREFERENCES_NAME = "AndroidAutoIdrive"
	enum class KEYS {
		ENABLED_NOTIFICATIONS,
		ENABLED_NOTIFICATIONS_POPUP,
		ENABLED_NOTIFICATIONS_POPUP_PASSENGER,
		ENABLED_GMAPS
	}
	private val DEFINITIONS = mapOf(
			KEYS.ENABLED_NOTIFICATIONS to SettingDefinition("Enabled_Notifications", "false", "Show phone notifications in the car"),
			KEYS.ENABLED_NOTIFICATIONS_POPUP to SettingDefinition("Enabled_Notifications_Popup", "true", "Show notification popups in the car"),
			KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER to SettingDefinition("Enabled_Notifications_Popup_Passenger", "false", "Show notification popups in the car when a passenger is seated"),
			KEYS.ENABLED_GMAPS to SettingDefinition("Enabled_GMaps", "false", "Show Google Maps in the car")
	)

	private val loadedSettings = HashMap<KEYS, String>()

	fun loadSettings(ctx: Context) {
		val preferences = ctx.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
		synchronized(loadedSettings) {
			loadedSettings.clear()
			KEYS.values().forEach { key ->
				val def = DEFINITIONS[key] ?: throw AssertionError("Missing SETTINGS definition: $key")
				val value = preferences.getString(def.name, def.default)
				loadedSettings[key] = value
			}
		}
	}

	operator fun get(key: KEYS): String {
		return getSetting(key)
	}
	fun getSetting(key: KEYS): String {
		synchronized(loadedSettings) {
			return loadedSettings[key]
					?: throw IllegalArgumentException("Missing SETTINGS definition: $key")
		}
	}

	fun saveSetting(ctx: Context, key: KEYS, value: String) {
		val setting = DEFINITIONS[key] ?: throw IllegalArgumentException("Missing SETTINGS definition: $key")
		val preferences = ctx.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
		val editor = preferences.edit()
		editor.putString(setting.name, value)
		editor.commit()
		loadedSettings[key] = value
	}
}