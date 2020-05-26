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
	private const val PREFERENCES_NAME = "AndroidAutoIdrive"

	enum class KEYS(val key: String, val default: String, val comment: String) {
		ENABLED_NOTIFICATIONS("Enabled_Notifications", "false", "Show phone notifications in the car"),
		ENABLED_NOTIFICATIONS_POPUP("Enabled_Notifications_Popup", "true", "Show notification popups in the car"),
		ENABLED_NOTIFICATIONS_POPUP_PASSENGER("Enabled_Notifications_Popup_Passenger", "false", "Show notification popups in the car when a passenger is seated"),
		ENABLED_GMAPS("Enabled_GMaps", "false", "Show Google Maps in the car"),
		MAP_WIDESCREEN("Map_Widescreen", "false", "Show Map in widescreen"),
		GMAPS_STYLE("GMaps_Style", "auto", "GMaps style"),
		AUDIO_SUPPORTS_USB("Audio_Supports_USB", (Build.VERSION.SDK_INT < Build.VERSION_CODES.O).toString(), "The phone is old enough to support USB accessory audio"),
		AUDIO_FORCE_CONTEXT("Audio_Force_Context", "false", "Force audio context"),
		AUDIO_DESIRED_APP("Audio_Desired_App", "", "Last music app that was playing")
	}

	private val loadedSettings = HashMap<KEYS, String>()

	fun loadDefaultSettings() {
		synchronized(loadedSettings) {
			loadedSettings.clear()
			KEYS.values().forEach { setting ->
				loadedSettings[setting] = setting.default
			}
		}
	}

	fun loadSettings(ctx: Context) {
		val preferences = ctx.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
		synchronized(loadedSettings) {
			loadedSettings.clear()
			KEYS.values().forEach { setting ->
				val value = preferences.getString(setting.key, setting.default) ?: setting.default
				loadedSettings[setting] = value
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
			return loadedSettings[key] ?: key.default
		}
	}

	fun saveSetting(ctx: Context, key: KEYS, value: String) {
		val preferences = ctx.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
		val editor = preferences.edit()
		editor.putString(key.key, value)
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