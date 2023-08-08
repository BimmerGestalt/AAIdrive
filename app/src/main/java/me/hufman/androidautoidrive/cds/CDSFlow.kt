package me.hufman.androidautoidrive.cds

import com.google.gson.JsonObject
import io.bimmergestalt.idriveconnectkit.CDSProperty
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.util.WeakHashMap

private val CDSDataFlowMaps = WeakHashMap<CDSData, CDSFlowMap>()
val CDSData.flow: CDSFlowMap
	get() = CDSDataFlowMaps.getOrPut(this) {
		CDSFlowManager(this)
	}

interface CDSFlowMap {
	var defaultIntervalLimit: Int
	operator fun get(property: CDSProperty): Flow<JsonObject>
}

/**
 * Implementation of the Flow factory
 * Returns Flow<JsonObject> objects
 * and dynamically registers them from the given CDSData
 */
class CDSFlowManager(private val cdsData: CDSData): CDSFlowMap {
	override var defaultIntervalLimit: Int = 500

	override fun get(property: CDSProperty): Flow<JsonObject> {
		return createFlow(property)
					.buffer(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
	}

	private fun createFlow(property: CDSProperty): Flow<JsonObject> = callbackFlow {
		val eventHandler = CDSEventHandler { _, propertyValue ->
			trySend(propertyValue)
		}
		cdsData.addEventHandler(property, defaultIntervalLimit, eventHandler)
		awaitClose {
			cdsData.removeEventHandler(property, eventHandler)
		}
	}
}