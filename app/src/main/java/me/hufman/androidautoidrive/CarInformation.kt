package me.hufman.androidautoidrive

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import me.hufman.androidautoidrive.carapp.CDSConnection
import me.hufman.androidautoidrive.carapp.CDSData
import me.hufman.androidautoidrive.carapp.CDSDataProvider
import me.hufman.idriveconnectionkit.CDSProperty
import java.util.*

open class CarInformation {
	companion object {
		@JvmStatic
		protected val CACHED_CAPABILITY_KEYS = setOf(
				"hmi.type",
				"navi",
				"tts"
		)

		private val _listeners = Collections.synchronizedMap(WeakHashMap<CarInformationObserver, Boolean>())
		val listeners: Map<CarInformationObserver, Boolean> = _listeners

		var currentCapabilities: Map<String, String> = emptyMap()
		var cachedCapabilities: Map<String, String> = emptyMap()
		val cdsData = CDSDataProvider()

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
			} catch (e: IllegalStateException) {
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

	open val capabilities: Map<String, String>
		get() = if (currentCapabilities.isEmpty()) {
			cachedCapabilities
		} else {
			currentCapabilities
		}

	open val cdsData: CDSData = CarInformation.cdsData
}

open class CarInformationUpdater(val appSettings: MutableAppSettings): CarInformation(), CarInformationDiscoveryListener {
	override var capabilities: Map<String, String>
		get() = super.capabilities
		set(value) {
			currentCapabilities = value
			cachedCapabilities = value.filterKeys { CACHED_CAPABILITY_KEYS.contains(it) }
			onCarCapabilities()
		}

	override fun onCapabilities(capabilities: Map<String, String?>) {
		this.capabilities = capabilities.mapValues { it.value ?: "" }
	}

	override val cdsData: CDSDataProvider = CarInformation.cdsData

	override fun onCdsConnection(connection: CDSConnection) {
		cdsData.setConnection(connection)
	}

	override fun onPropertyChangedEvent(property: CDSProperty, propertyValue: JsonObject) {
		cdsData.onPropertyChangedEvent(property, propertyValue)
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