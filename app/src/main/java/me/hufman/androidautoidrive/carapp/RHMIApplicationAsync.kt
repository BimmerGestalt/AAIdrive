package me.hufman.androidautoidrive.carapp

import android.os.Handler
import me.hufman.idriveconnectionkit.rhmi.*

class RHMIApplicationAsync(val app: RHMIApplication, val handler: Handler): RHMIApplication() {
	override val models = app.models
	override val actions = app.actions
	override val events = app.events
	override val states = app.states
	override val components = app.components

	override fun setModel(modelId: Int, value: Any) {
		handler.post { app.setModel(modelId, value) }
	}

	override fun setProperty(componentId: Int, propertyId: Int, value: Any) {
		handler.post { app.setProperty(componentId, propertyId, value) }
	}

	override fun triggerHMIEvent(eventId: Int, args: Map<Any, Any?>) {
		handler.post { app.triggerHMIEvent(eventId, args) }
	}
}