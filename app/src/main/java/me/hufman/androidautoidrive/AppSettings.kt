package me.hufman.androidautoidrive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler

const val INTENT_GMAP_RELOAD_SETTINGS = "me.hufman.androidautoidrive.carapp.gmaps.RELOAD_SETTINGS"

object AppSettings {
	class SettingDefinition (val name: String, val default: String, val comment: String)

	private const val PREFERENCES_NAME = "AndroidAutoIdrive"
	enum class KEYS {
		ENABLED_NOTIFICATIONS,
		ENABLED_NOTIFICATIONS_POPUP,
		ENABLED_NOTIFICATIONS_POPUP_PASSENGER,
		ENABLED_GMAPS,
		MAP_WIDESCREEN,
		GMAPS_STYLE,
		AUDIO_SUPPORTS_USB,
		AUDIO_FORCE_CONTEXT,
		AUDIO_DESIRED_APP
	}
	private val DEFINITIONS = mapOf(
		KEYS.ENABLED_NOTIFICATIONS to SettingDefinition("Enabled_Notifications", "false", "Show phone notifications in the car"),
		KEYS.ENABLED_NOTIFICATIONS_POPUP to SettingDefinition("Enabled_Notifications_Popup", "true", "Show notification popups in the car"),
		KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER to SettingDefinition("Enabled_Notifications_Popup_Passenger", "false", "Show notification popups in the car when a passenger is seated"),
		KEYS.ENABLED_GMAPS to SettingDefinition("Enabled_GMaps", "false", "Show Google Maps in the car"),
		KEYS.MAP_WIDESCREEN to SettingDefinition("Map_Widescreen", "false", "Show Map in widescreen"),
		KEYS.GMAPS_STYLE to SettingDefinition("GMaps_Style", "auto", "GMaps style"),
		KEYS.AUDIO_SUPPORTS_USB to SettingDefinition("Audio_Supports_USB", (Build.VERSION.SDK_INT < Build.VERSION_CODES.O).toString(), "The phone is old enough to support USB accessory audio"),
		KEYS.AUDIO_FORCE_CONTEXT to SettingDefinition("Audio_Force_Context", "false", "Force audio context"),
		KEYS.AUDIO_DESIRED_APP to SettingDefinition("Audio_Desired_App", "", "Last music app that was playing")
	)

	private val loadedSettings = HashMap<KEYS, String>()

	fun loadDefaultSettings() {
		synchronized(loadedSettings) {
			loadedSettings.clear()
			KEYS.values().forEach { key ->
				val def = DEFINITIONS[key] ?: throw AssertionError("Missing SETTINGS definition: $key")
				loadedSettings[key] = def.default
			}
		}
	}

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

	fun tempSetSetting(key: KEYS, value: String) {
		synchronized(loadedSettings) {
			loadedSettings[key] = value
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
		editor.apply()
		loadedSettings[key] = value
	}
}

/**
 * An instantiated object to pass around to get/change settings
 * It can also have a callback registered
 */
class MutableAppSettings(val context: Context, val handler: Handler? = null) {
	companion object {
		val INTENT_SETTINGS_CHANGED = "me.hufman.androidautoidrive.notifications.INTENT_SETTINGS_CHANGED"
	}

	val receiver = object: BroadcastReceiver() {
		override fun onReceive(p0: Context?, p1: Intent?) {
			callback?.invoke()
		}
	}
	var callback: (() -> Unit)? = null
		set(value) {
			if (field == null && value != null) {
				context.registerReceiver(receiver, IntentFilter(INTENT_SETTINGS_CHANGED), null, handler)
			}
			if (field != null && value == null) {
				context.unregisterReceiver(receiver)
			}
			field = value
		}

	fun getSetting(key: AppSettings.KEYS): String {
		return AppSettings.getSetting(key)
	}
	fun saveSetting(key: AppSettings.KEYS, value: String) {
		AppSettings.saveSetting(context, key, value)

		context.sendBroadcast(Intent(INTENT_SETTINGS_CHANGED))
	}

	operator fun get(key: AppSettings.KEYS): String {
		return this.getSetting(key)
	}
	operator fun set(key: AppSettings.KEYS, value: String) {
		this.saveSetting(key, value)
	}
}