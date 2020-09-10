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
		DONATION_DAYS_COUNT("Donation_Days_Count", "0", "Number of days that the user has used the app, counting towards the donation request threshold"),
		DONATION_LAST_DAY("Donation_Last_Day", "2000-01-01", "The last day that the user used the app"),
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

/**
 * A set that is backed by a comma-separated string in AppSettings
 */
class ListSetting(val appSettings: MutableAppSettings, val key: AppSettings.KEYS): MutableSet<String> {
	fun getAll(): MutableSet<String> {
		return appSettings[key].split(",").toMutableSet()
	}
	fun setAll(values: Set<String>) {
		val newSetting = values.joinToString(",")
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