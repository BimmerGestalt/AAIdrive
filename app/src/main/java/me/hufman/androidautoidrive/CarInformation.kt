package me.hufman.androidautoidrive

import java.util.*

open class CarInformation {
	companion object {
		private val _listeners = Collections.synchronizedMap(WeakHashMap<CarInformationObserver, Boolean>())
		val listeners: Map<CarInformationObserver, Boolean> = _listeners

		var currentCapabilities: Map<String, String> = emptyMap()

		fun addListener(listener: CarInformationObserver) {
			_listeners[listener] = true
		}

		fun onCarCapabilities() {
			listeners.keys.toList().forEach {
				it.onCarCapabilities(currentCapabilities)
			}
		}
	}

	var capabilities: Map<String, String>
		get() = currentCapabilities
		set(value) {
			currentCapabilities = value
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