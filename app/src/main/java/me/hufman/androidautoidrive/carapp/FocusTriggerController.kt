package me.hufman.androidautoidrive.carapp

import de.bmw.idrive.BMWRemoting
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import me.hufman.idriveconnectionkit.rhmi.RHMIEvent
import me.hufman.idriveconnectionkit.rhmi.RHMIState

class FocusTriggerController(val focusEvent: RHMIEvent.FocusEvent, val recreateCallback: () -> Unit) {
	var hasFocusedState = false

	fun focusState(state: RHMIState, recreate: Boolean): Boolean {
		hasFocusedState = true
		return try {
			focusEvent.triggerEvent(mapOf(
				0.toByte() to state.id
			))
			true
		} catch (e: BMWRemoting.ServiceException) {
			if (recreate) {
				recreateCallback()
				focusState(state, false)
			} else {
				false
			}
		}
	}

	fun focusComponent(component: RHMIComponent, listIndex: Int? = 0) {
		val args = mutableMapOf<Any, Any?>(0.toByte() to component.id)
		if (listIndex != null) {
			args[41.toByte()] = listIndex
		}
		focusEvent.triggerEvent(args)
	}
}