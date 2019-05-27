package me.hufman.androidautoidrive.carapp

import me.hufman.idriveconnectionkit.rhmi.RHMIApplication

class RHMIApplicationIdempotent(val app: RHMIApplication): RHMIApplication() {
	override val models = app.models
	override val actions = app.actions
	override val events = app.events
	override val states = app.states
	override val components = app.components

	override fun setModel(modelId: Int, value: Any) {
		if (models[modelId] != value)
			app.setModel(modelId, value)
	}

	override fun setProperty(componentId: Int, propertyId: Int, value: Any) {
		if (components[componentId]?.properties?.get(propertyId) != value)
			app.setProperty(componentId, propertyId, value)
	}

	override fun triggerHMIEvent(eventId: Int, args: Map<Any, Any?>) {
		app.triggerHMIEvent(eventId, args)
	}
}