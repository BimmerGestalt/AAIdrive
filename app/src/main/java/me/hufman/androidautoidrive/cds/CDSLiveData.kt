package me.hufman.androidautoidrive.cds

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.JsonObject
import io.bimmergestalt.idriveconnectkit.CDSProperty
import java.util.*

/**
 * An extension to CDSData that allows accessing CDS via LiveData
 */

private val CDSDataLiveDataMaps = WeakHashMap<CDSData, CDSLiveDataMap>()
val CDSData.liveData: CDSLiveDataMap
	get() = CDSDataLiveDataMaps.getOrPut(this) {
		CDSLiveDataManager(this)
	}

/**
 * The LiveData factory
 * It is keyed by the CDSProperty to watch
 */
interface CDSLiveDataMap {
	var defaultIntervalLimit: Int
	operator fun get(property: CDSProperty): LiveData<JsonObject>
}

/**
 * Implementation of the LiveData factory
 * Returns LiveData<JsonObject> objects
 * and dynamically registers them from the given CDSConnection
 */
class CDSLiveDataManager(private val cdsData: CDSData): CDSLiveDataMap {
	override var defaultIntervalLimit: Int = 500
	private val _subscriptions = HashMap<CDSProperty, MutableLiveData<JsonObject>>()

	override operator fun get(property: CDSProperty): LiveData<JsonObject> {
		return _subscriptions.getOrPut(property) {
			CDSMutableLiveData(cdsData, property, defaultIntervalLimit)
		}
	}
}

/**
 * A LiveData subclass that dynamically subscribes to the given CDSConnection
 * Expects to be created and updated by CDSLiveDataManager
 */
class CDSMutableLiveData(private val cdsData: CDSData, val property: CDSProperty, private val intervalLimit: Int): MutableLiveData<JsonObject>(), CDSEventHandler {
	init {
		val value = cdsData[property]
		if (value != null) {
			postValue(value)
		}
	}

	override fun onActive() {
		super.onActive()
		cdsData[property]?.also { postValue(it) }       // update with latest value if it's been updated
		cdsData.addEventHandler(property, intervalLimit, this)      // subscribe to updates
	}

	override fun onInactive() {
		super.onInactive()
		cdsData.removeEventHandler(property, this)
	}

	override fun onPropertyChangedEvent(property: CDSProperty, propertyValue: JsonObject) {
		// postValue because CDSEventHandler might be coming from the Etch connection
		if (this.value != propertyValue) {
			this.postValue(propertyValue)
		}
	}
}