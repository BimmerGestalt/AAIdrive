package me.hufman.androidautoidrive.cds

import android.os.Handler
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import de.bmw.idrive.BMWRemotingServer
import io.bimmergestalt.idriveconnectkit.CDSProperty
import java.util.HashMap
import java.util.HashSet

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
			carConnection.cds_addPropertyChangedEventHandler(_cdsHandle, property.ident.toString(), property.propertyName, intervalLimit)
			carConnection.cds_getPropertyAsync(_cdsHandle, property.ident.toString(), property.propertyName)
		}
	}

	override fun unsubscribeProperty(property: CDSProperty) {
		synchronized(carConnection) {
			carConnection.cds_removePropertyChangedEventHandler(_cdsHandle, property.ident.toString(), property.propertyName)
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
		val subscribed = _eventHandlers.contains(property)
		_eventHandlers.getOrPut(property, { HashSet() }).add(eventHandler)
		_connection.subscribeProperty(property, intervalLimit)
		val existing = _data[property]
		if (subscribed && existing != null) {
			eventHandler.onPropertyChangedEvent(property, existing)
		}
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
