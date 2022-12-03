package me.hufman.androidautoidrive.carapp

import android.util.SparseArray
import io.bimmergestalt.idriveconnectkit.rhmi.*
import me.hufman.androidautoidrive.utils.forEach
import me.hufman.androidautoidrive.utils.setDefault

/** An RHMIApplication wrapper that can change out its wrapped app
 *  The UI Layout must be loaded into this level, not in the wrapped app
 *  Any property and setData and event triggers get forwarded to the underlying app
 *
 *  When reconnecting to a new app, any previously set properties and most models are synced
 *  Notably, RaImageModels and RaListModels are not
 *  No triggered events are replayed either
 *  Make sure to handle these manually (for example, multimediaInfoEvent, AudioHmiState's coverart)
 */
class RHMIApplicationSwappable(var app: RHMIApplication): RHMIApplication(), RHMIApplicationWrapper {
	var isConnected = true
		set(value) {
			field = value
			if (value) {
				syncToApp()
			}
		}

	override val models = HashMap<Int, RHMIModel>()
	override val actions = HashMap<Int, RHMIAction>()
	override val events = HashMap<Int, RHMIEvent>()
	override val states = HashMap<Int, RHMIState>()
	override val components = HashMap<Int, RHMIComponent>()

	// remember any applied properties and data
	val desiredData = SparseArray<Any>()
	val desiredProperties = SparseArray<SparseArray<Any?>>()

	override fun setModel(modelId: Int, value: Any) {
		val model = models[modelId]
		val memoized = when(model) {
			is RHMIModel.RaIntModel -> true
			is RHMIModel.RaDataModel -> true
			is RHMIModel.RaBoolModel -> true
			is RHMIModel.TextIdModel -> true
			is RHMIModel.ImageIdModel -> true
			else -> false
		}
		if (memoized) {
			desiredData.put(modelId, value)
		} else {
			desiredData.remove(modelId)
		}

		if (isConnected) app.setModel(modelId, value)
	}

	override fun setProperty(componentId: Int, propertyId: Int, value: Any?) {
		val properties = desiredProperties.setDefault(componentId) { SparseArray() }
		properties.put(propertyId, value)

		if (isConnected) app.setProperty(componentId, propertyId, value)
	}

	override fun triggerHMIEvent(eventId: Int, args: Map<Any, Any?>) {
		if (isConnected) app.triggerHMIEvent(eventId, args)
	}

	/**
	 * Apply any desiredData and desiredProperties to the app
	 */
	fun syncToApp() {
		desiredData.forEach { modelId, value -> app.setModel(modelId, value) }
		desiredProperties.forEach { componentId, properties  ->
			properties.forEach { propertyId, value ->
				app.setProperty(componentId, propertyId, value)
			}
		}
	}

	override fun unwrap(): RHMIApplication {
		val app = app
		return if (app is RHMIApplicationWrapper) {
			app.unwrap()
		} else {
			return app
		}
	}
}