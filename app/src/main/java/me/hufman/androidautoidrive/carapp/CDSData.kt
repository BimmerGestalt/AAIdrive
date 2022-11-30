package me.hufman.androidautoidrive.carapp

import android.os.Handler
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import de.bmw.idrive.BMWRemotingServer
import io.bimmergestalt.idriveconnectkit.CDSProperty
import java.util.*

/**
 * Manages CDS subscriptions to the car
 */
interface CDSConnection {
	fun subscribeProperty(property: CDSProperty, intervalLimit: Int)
	fun unsubscribeProperty(property: CDSProperty)
}

/**
 * An implementation of CDSConnection backed by a BMWRemotingServer
 * It handles creating the cds handle, so
 * only create one CDSConnectionEtch per BMWRemotingServer
 */
class CDSConnectionEtch(private val carConnection: BMWRemotingServer): CDSConnection {
	private val _cdsHandle = synchronized(carConnection) { carConnection.cds_create() }

	override fun subscribeProperty(property: CDSProperty, intervalLimit: Int) {
		synchronized(carConnection) {
			carConnection.cds_addPropertyChangedEventHandler(_cdsHandle, property.propertyName, property.ident.toString(), intervalLimit)
			carConnection.cds_getPropertyAsync(_cdsHandle, property.ident.toString(), property.propertyName)
		}
	}

	override fun unsubscribeProperty(property: CDSProperty) {
		synchronized(carConnection) {
			carConnection.cds_removePropertyChangedEventHandler(_cdsHandle, property.propertyName, property.ident.toString())
		}
	}
}

/**
 * A CDSConnection that is backed by a CDSData object and the given CDSEventHandler
 */
class CDSDataConnectionWrapper(private val cdsData: CDSData, private val eventHandler: CDSEventHandler): CDSConnection {
	override fun subscribeProperty(property: CDSProperty, intervalLimit: Int) {
		cdsData.addEventHandler(property, intervalLimit, eventHandler)
		val existing = cdsData[property]
		if (existing != null) {
			eventHandler.onPropertyChangedEvent(property, existing)
		}
	}

	override fun unsubscribeProperty(property: CDSProperty) {
		cdsData.removeEventHandler(property, eventHandler)
	}
}

class CDSConnectionAsync(val handler: Handler, val connection: CDSConnection): CDSConnection {
	override fun subscribeProperty(property: CDSProperty, intervalLimit: Int) {
		handler.post { connection.subscribeProperty(property, intervalLimit) }
	}

	override fun unsubscribeProperty(property: CDSProperty) {
		handler.post { connection.unsubscribeProperty(property) }
	}
}

class CDSConnectionBreakable: CDSConnection {
	var connection: CDSConnection? = null
		set(value) {
			field = value
			if (value != null) {
				_intervals.forEach {
					_subscribeProperty(it.key, it.value)
				}
			}
		}

	// the intended subscription intervals
	// will be applied to any wrapped connection when set above
	private val _intervals = HashMap<CDSProperty, Int>()

	// wraps the subscription with error handling to drop the connection
	private fun _subscribeProperty(property: CDSProperty, intervalLimit: Int) {
		try {
			connection?.subscribeProperty(property, intervalLimit)
		} catch (e: Exception) {
			connection = null
		}
	}

	override fun subscribeProperty(property: CDSProperty, intervalLimit: Int) {
		val previousLimit = _intervals[property]
		if (previousLimit == null || intervalLimit < previousLimit) {
			_intervals[property] = intervalLimit
			_subscribeProperty(property, intervalLimit)
		}
	}

	// wraps the subscription with error handling to drop the connection
	private fun _unsubscribeProperty(property: CDSProperty) {
		try {
			connection?.unsubscribeProperty(property)
		} catch (e: Exception) {
			connection = null
		}
	}

	override fun unsubscribeProperty(property: CDSProperty) {
		_intervals.remove(property)
		_unsubscribeProperty(property)
	}
}

/**
 * Notified about new CDS data values
 */
interface CDSEventHandler {
	fun onPropertyChangedEvent(property: CDSProperty, propertyValue: JsonObject)
}

/**
 * Helper function to parse from the car's callback to CDSEventHandler
 */
fun CDSEventHandler.onPropertyChangedEvent(ident: String?, propertyValue: String?) {
	val property = CDSProperty.fromIdent(ident) ?: return
	propertyValue ?: return
	val jsonObject = try {
		JsonParser.parseString(propertyValue) as? JsonObject
	} catch (e: JsonSyntaxException) { null } ?: return
	this.onPropertyChangedEvent(property, jsonObject)
}

fun CDSEventHandler(block: (property: CDSProperty, propertyValue: JsonObject) -> Unit): CDSEventHandler {
	return object: CDSEventHandler{
		override fun onPropertyChangedEvent(property: CDSProperty, propertyValue: JsonObject) {
			block(property, propertyValue)
		}
	}
}

/**
 * A low-level module to register CDS event listeners and read data
 */
interface CDSData {
	operator fun get(key: CDSProperty): JsonObject?
	fun addEventHandler(property: CDSProperty, intervalLimit: Int, eventHandler: CDSEventHandler)
	fun removeEventHandler(property: CDSProperty, eventHandler: CDSEventHandler)
}

/**
 * A writable interface to the CDSData, to be notified from the BMWRemotingClient
 */
class CDSDataProvider: CDSData, CDSEventHandler {
	private val _data = HashMap<CDSProperty, JsonObject>()
	override operator fun get(key: CDSProperty): JsonObject? = _data[key]

	fun clear() = _data.clear()

	private var _connection = CDSConnectionBreakable()

	/**
	 * Sets the underlying connection
	 * Any previous subscriptions will be applied
	 */
	fun setConnection(connection: CDSConnection?) {
		_connection.connection = connection
	}

	private val _eventHandlers = HashMap<CDSProperty, MutableSet<CDSEventHandler>>()
	override fun addEventHandler(property: CDSProperty, intervalLimit: Int, eventHandler: CDSEventHandler) {
		_eventHandlers.getOrPut(property, {HashSet()}).add(eventHandler)
		_connection.subscribeProperty(property, intervalLimit)
	}

	override fun removeEventHandler(property: CDSProperty, eventHandler: CDSEventHandler) {
		_eventHandlers[property]?.remove(eventHandler)

		if (_eventHandlers[property]?.isEmpty() == true) {
			_eventHandlers.remove(property)
			_connection.unsubscribeProperty(property)
		}
	}

	override fun onPropertyChangedEvent(property: CDSProperty, propertyValue: JsonObject) {
		_data[property] = propertyValue
		_eventHandlers[property]?.toSet()?.forEach { it.onPropertyChangedEvent(property, propertyValue) }
	}

	/** Use this CDSData object as a CDSConnection */
	fun asConnection(eventHandler: CDSEventHandler): CDSConnection {
		return CDSDataConnectionWrapper(this, eventHandler)
	}
}

/**
 * An extension to CDSData to support subscribing to specific elements
 */
val CDSDataSubscriptions = WeakHashMap<CDSData, CDSSubscriptions>()
val CDSData.subscriptions: CDSSubscriptions
	get() = CDSDataSubscriptions.getOrPut(this) {
		CDSSubscriptionsManager(this)
	}

/**
 * The subscription manager
 * It is keyed by the CDSProperty to watch
 * Set to a callback lambda, or to null to clear a callback
 */
interface CDSSubscriptions {
	var defaultIntervalLimit: Int
	operator fun set(property: CDSProperty, subscription: ((JsonObject) -> Unit)?)
	operator fun get(property: CDSProperty): ((JsonObject) -> Unit)?
	fun addSubscription(property: CDSProperty, intervalLimit: Int = defaultIntervalLimit, subscription: (JsonObject) -> Unit)
	fun removeSubscription(property: CDSProperty)
}

/**
 * Implementation of the subscription manager
 */
class CDSSubscriptionsManager(private val cdsData: CDSData): CDSEventHandler, CDSSubscriptions {
	override var defaultIntervalLimit: Int = 500
	private val _subscriptions = HashMap<CDSProperty, (JsonObject) -> Unit>()

	override operator fun set(property: CDSProperty, subscription: ((value: JsonObject) -> Unit)?) {
		if (subscription != null) {
			addSubscription(property, defaultIntervalLimit, subscription)
		} else {
			removeSubscription(property)
		}
	}

	override fun get(property: CDSProperty): ((JsonObject) -> Unit)? {
		return _subscriptions[property]
	}

	override fun addSubscription(property: CDSProperty, intervalLimit: Int, subscription: (value: JsonObject) -> Unit) {
		_subscriptions[property] = subscription
		cdsData.addEventHandler(property, intervalLimit, this)
	}

	override fun removeSubscription(property: CDSProperty) {
		cdsData.removeEventHandler(property, this)
		_subscriptions.remove(property)
	}

	override fun onPropertyChangedEvent(property: CDSProperty, propertyValue: JsonObject) {
		_subscriptions[property]?.invoke(propertyValue)
	}
}

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