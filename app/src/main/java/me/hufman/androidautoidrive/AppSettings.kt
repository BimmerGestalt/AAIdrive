package me.hufman.androidautoidrive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import org.json.JSONArray

/**
 * This implements read-only access to a singleton of loaded settings
 * This singleton will only return defaults until AppSettings.loadSettings(context) loads persisted settings
 */
interface AppSettings {
	enum class KEYS(val key: String, val default: String, val comment: String) {
		ENABLED_NOTIFICATIONS("Enabled_Notifications", "false", "Show phone notifications in the car"),
		ENABLED_NOTIFICATIONS_POPUP("Enabled_Notifications_Popup", "true", "Show notification popups in the car"),
		ENABLED_NOTIFICATIONS_POPUP_PASSENGER("Enabled_Notifications_Popup_Passenger", "false", "Show notification popups in the car when a passenger is seated"),
		NOTIFICATIONS_SOUND("Notifications_Sound", "true", "Play a notification sound"),
		NOTIFICATIONS_READOUT("Notifications_Readout", "false", "Viewing a notification also reads it aloud"),
		NOTIFICATIONS_READOUT_POPUP("Notifications_Readout_Popup", "false", "New notifications are read aloud"),
		NOTIFICATIONS_READOUT_POPUP_PASSENGER("Notifications_Readout_Popup_Passenger", "false", "New notifications are read aloud when a passenger is seated"),
		ENABLED_GMAPS("Enabled_GMaps", "false", "Show Google Maps in the car"),
		MAP_WIDESCREEN("Map_Widescreen", "false", "Show Map in widescreen"),
		GMAPS_STYLE("GMaps_Style", "auto", "GMaps style"),
		AUDIO_SUPPORTS_USB("Audio_Supports_USB", (Build.VERSION.SDK_INT < Build.VERSION_CODES.O).toString(), "The phone is old enough to support USB accessory audio"),
		AUDIO_FORCE_CONTEXT("Audio_Force_Context", "false", "Force audio context"),
		AUDIO_DESIRED_APP("Audio_Desired_App", "", "Last music app that was playing"),
		SHOW_ADVANCED_SETTINGS("Advanced_Settings", "false", "Show advanced settings"),
		HIDDEN_MUSIC_APPS("Hidden_Music_Apps", "com.android.bluetooth,com.clearchannel.iheartradio.connect,com.google.android.googlequicksearchbox,com.google.android.youtube,com.vanced.android.youtube", "List of music apps to hide from the app list"),
		FORCE_SPOTIFY_LAYOUT("Force_Spotify_Layout", "false", "Use Spotify UI Resources"),
		DONATION_DAYS_COUNT("Donation_Days_Count", "0", "Number of days that the user has used the app, counting towards the donation request threshold"),
		DONATION_LAST_DAY("Donation_Last_Day", "2000-01-01", "The last day that the user used the app"),
	}

	/** Store the active preferences in a singleton */
	companion object {
		private const val PREFERENCES_NAME = "AndroidAutoIdrive"

		private val loadedSettings = HashMap<KEYS, String>()

		/**
		 * Initialize the current settings with defaults
		 * which will undo any loadSettings that may have happened
		 */
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

		operator fun get(key: KEYS): String {
			return getSetting(key)
		}
		private fun getSetting(key: KEYS): String {
			synchronized(loadedSettings) {
				return loadedSettings[key] ?: key.default
			}
		}

		fun tempSetSetting(key: KEYS, value: String) {
			synchronized(loadedSettings) {
				loadedSettings[key] = value
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

	/** Convenience function for getting a setting */
	operator fun get(key: KEYS): String
}

/** A concrete class to read settings from the AppSettings singleton */
class AppSettingsViewer: AppSettings {
	override operator fun get(key: AppSettings.KEYS): String {
		return AppSettings[key]
	}
}

/**
 * An interface for modifying the settings
 */
interface MutableAppSettings: AppSettings {
	operator fun set(key: AppSettings.KEYS, value: String)
}

/**
 * An interface for being notified on changes to the settings
 */
interface AppSettingsObserver: AppSettings {
	var callback: (() -> Unit)?
}

/**
 * A combined interface for modifying the settings and being notified on changes
 */
interface MutableAppSettingsObserver: AppSettingsObserver, MutableAppSettings {
}

/**
 * An isolated MutableAppSettings object, unrelated to the AppSettings singleton settings
 * By default it returns all the default settings values
 */
class MockAppSettings(vararg settings: Pair<AppSettings.KEYS, String> = emptyArray()): MutableAppSettingsObserver {
	override var callback: (() -> Unit)? = null
	val settings = mutableMapOf(*settings)
	override operator fun get(key: AppSettings.KEYS): String {
		return settings[key] ?: key.default
	}
	override operator fun set(key: AppSettings.KEYS, value: String) {
		settings[key] = value
		callback?.invoke()
	}
}

/**
 * A MutableAppSettings object that modifies the global singleton and notifies other instances
 * Clients should set a callback to subscribe, and they should set it to null to unsubscribe to avoid leaking
 * Clients can specify a custom handler on which to receive the callback
 */
class MutableAppSettingsReceiver(val context: Context, val handler: Handler? = null): MutableAppSettingsObserver {
	companion object {
		val INTENT_SETTINGS_CHANGED = "me.hufman.androidautoidrive.INTENT_SETTINGS_CHANGED"
	}

	val receiver = object: BroadcastReceiver() {
		override fun onReceive(p0: Context?, p1: Intent?) {
			callback?.invoke()
		}
	}
	override var callback: (() -> Unit)? = null
		set(value) {
			if (field == null && value != null) {
				context.registerReceiver(receiver, IntentFilter(INTENT_SETTINGS_CHANGED), null, handler)
			}
			if (field != null && value == null) {
				context.unregisterReceiver(receiver)
			}
			field = value
		}

	override operator fun get(key: AppSettings.KEYS): String {
		return AppSettings[key]
	}

	override operator fun set(key: AppSettings.KEYS, value: String) {
		AppSettings.saveSetting(context, key, value)

		context.sendBroadcast(Intent(INTENT_SETTINGS_CHANGED))
	}
}

/**
 * A set that is backed by a comma-separated string in AppSettings
 */
class StoredSet(val appSettings: MutableAppSettings, val key: AppSettings.KEYS): MutableSet<String> {
	fun getAll(): MutableSet<String> {
		val stringValue = appSettings[key]
		 // prefer json but support comma-separated strings for legacy settings
		return try {
			if (stringValue.isNotEmpty() && stringValue[0] == '[') {
				val parsedJson = JSONArray(stringValue)
				HashSet<String>(parsedJson.length()).apply {
					(0 until parsedJson.length()).forEach {
						add(parsedJson.getString(it))
					}
				}
			} else {
				appSettings[key].split(",").toMutableSet()
			}
		} catch (e: Exception) { HashSet() }
	}
	fun setAll(values: Set<String>) {
		val newSetting = JSONArray(values.sorted()).toString()
		if (appSettings[key] != newSetting) {
			appSettings[key] = newSetting
		}
	}
	inline fun <K> withSet(callback: MutableSet<String>.() -> K): K {
		val values = getAll()
		val response = callback(values)
		setAll(values)
		return response
	}

	override fun add(element: String): Boolean = withSet {
		add(element)
	}

	override fun addAll(elements: Collection<String>): Boolean = withSet {
		addAll(elements)
	}

	override fun clear() = withSet {
		clear()
	}

	override fun iterator(): MutableIterator<String> = withSet {
		iterator()
	}

	override fun remove(element: String): Boolean = withSet {
		remove(element)
	}

	override fun removeAll(elements: Collection<String>): Boolean = withSet {
		removeAll(elements)
	}

	override fun retainAll(elements: Collection<String>): Boolean = withSet {
		retainAll(elements)
	}

	override val size: Int
		get() = withSet {
			size
		}

	override fun contains(element: String): Boolean = withSet {
		contains(element)
	}

	override fun containsAll(elements: Collection<String>): Boolean = withSet {
		containsAll(elements)
	}

	override fun isEmpty(): Boolean = withSet {
		isEmpty()
	}
}