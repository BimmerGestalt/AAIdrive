package me.hufman.androidautoidrive

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import me.hufman.androidautoidrive.carapp.CDSConnection
import me.hufman.androidautoidrive.carapp.CDSData
import me.hufman.androidautoidrive.carapp.CDSDataProvider
import me.hufman.androidautoidrive.carapp.CDSEventHandler
import me.hufman.idriveconnectionkit.CDS
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

		private val CACHED_CDS_INTERVAL = 10000
		@JvmStatic
		protected val CACHED_CDS_KEYS = setOf(
				CDS.VEHICLE.LANGUAGE
		)

		private val _listeners = Collections.synchronizedMap(WeakHashMap<CarInformationObserver, Boolean>())
		val listeners: Map<CarInformationObserver, Boolean> = _listeners

		var currentCapabilities: Map<String, String> = emptyMap()
		var cachedCapabilities: Map<String, String> = emptyMap()
		val cdsData = CDSDataProvider()
		var cachedCdsData = CDSDataProvider().also { cachedCdsData ->
			CACHED_CDS_KEYS.forEach { cachedProperty ->
				cdsData.addEventHandler(cachedProperty, CACHED_CDS_INTERVAL, cachedCdsData)
			}
		}

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

			val cdsCached = settings[AppSettings.KEYS.CACHED_CAR_DATA]
			try {
				val loaded = JsonParser.parseString(cdsCached).asJsonObject
				CACHED_CDS_KEYS.forEach { propertyKey ->
					val cachedProperty = loaded[propertyKey.propertyName]?.asJsonObject
					if (cachedProperty != null) {
						cachedCdsData.onPropertyChangedEvent(propertyKey, cachedProperty)
					}
				}
			} catch (e: JsonSyntaxException) {
				Log.w(TAG, "Failed to restore cached cds properties", e)
			} catch (e: IllegalStateException) {
				Log.w(TAG, "Failed to restore cached cds properties", e)
			}
		}

		fun saveCache(settings: MutableAppSettings) {
			val capabilities = JsonObject()
			cachedCapabilities.keys.forEach { key ->
				capabilities.addProperty(key, cachedCapabilities[key])
			}
			settings[AppSettings.KEYS.CACHED_CAR_CAPABILITIES] = capabilities.toString()

			val cdsCached = JsonObject()
			CACHED_CDS_KEYS.forEach { propertyKey ->
				val value = cachedCdsData[propertyKey]
				if (value != null) {
					cdsCached.add(propertyKey.propertyName, value)
				}
			}
			settings[AppSettings.KEYS.CACHED_CAR_DATA] = cdsCached.toString()
		}
	}

	open val capabilities: Map<String, String>
		get() = if (currentCapabilities.isEmpty()) {
			cachedCapabilities
		} else {
			currentCapabilities
		}

	open val cdsData: CDSData = CarInformation.cdsData
	open val cachedCdsData: CDSData = CarInformation.cachedCdsData
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
	override val cachedCdsData: CDSDataProvider = CarInformation.cachedCdsData

	override fun onCdsConnection(connection: CDSConnection) {
		cdsData.setConnection(connection)
	}

	override fun onPropertyChangedEvent(property: CDSProperty, propertyValue: JsonObject) {
		cdsData.onPropertyChangedEvent(property, propertyValue)
		if (CACHED_CDS_KEYS.contains(property)) {
			saveCache(appSettings)
		}
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