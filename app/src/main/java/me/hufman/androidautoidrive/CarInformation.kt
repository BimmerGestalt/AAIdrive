package me.hufman.androidautoidrive

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.util.*

open class CarInformation {
	companion object {
		private val CACHED_CAPABILITY_KEYS = setOf(
				"hmi.type",
				"navi",
				"tts"
		)
		private val _listeners = Collections.synchronizedMap(WeakHashMap<CarInformationObserver, Boolean>())
		val listeners: Map<CarInformationObserver, Boolean> = _listeners

		var currentCapabilities: Map<String, String> = emptyMap()
		var cachedCapabilities: Map<String, String> = emptyMap()

		fun addListener(listener: CarInformationObserver) {
			_listeners[listener] = true
		}

		fun onCarCapabilities() {
			listeners.keys.toList().forEach {
				it.onCarCapabilities(currentCapabilities)
			}
		}

		fun loadCache(settings: AppSettings) {
			val capabilities = settings[AppSettings.KEYS.CACHED_CAR_CAPABILITIES]
			try {
				val loaded = JsonParser.parseString(capabilities).asJsonObject
				cachedCapabilities = loaded.keySet().map {
					it to loaded[it].asString
				}.toMap()
			} catch (e: JsonSyntaxException) {
				Log.w(TAG, "Failed to restore cached capabilities", e)
			}
		}

		fun saveCache(settings: MutableAppSettings) {
			val capabilities = JsonObject()
			cachedCapabilities.keys.forEach { key ->
				capabilities.addProperty(key, cachedCapabilities[key])
			}
			settings[AppSettings.KEYS.CACHED_CAR_CAPABILITIES] = capabilities.toString()
		}
	}

	var capabilities: Map<String, String>
		get() = if (currentCapabilities.isEmpty()) {
			cachedCapabilities
		} else {
			currentCapabilities
		}
		set(value) {
			currentCapabilities = value
			cachedCapabilities = value.filterKeys { CACHED_CAPABILITY_KEYS.contains(it) }
			onCarCapabilities()
		}
}

class CarInformationObserver(var callback: (Map<String, String>) -> Unit = {}): CarInformation() {
	init {
		addListener(this)
	}

	fun onCarCapabilities(capabilities: Map<String, String>) {
		callback.invoke(capabilities)
	}
}