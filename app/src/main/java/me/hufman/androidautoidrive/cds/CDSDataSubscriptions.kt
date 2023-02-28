package me.hufman.androidautoidrive.cds

import com.google.gson.JsonObject
import io.bimmergestalt.idriveconnectkit.CDSProperty
import java.util.*


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
