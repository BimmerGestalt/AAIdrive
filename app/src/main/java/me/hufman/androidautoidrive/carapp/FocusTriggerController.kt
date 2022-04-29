package me.hufman.androidautoidrive.carapp

import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIComponent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIEvent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState

class FocusTriggerController(val focusEvent: RHMIEvent.FocusEvent, val recreateCallback: () -> Unit) {
	var hasFocusedState = false     // whether we have triggered an HMI FocusEvent in the car

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

	fun focusComponent(component: RHMIComponent, listIndex: Int? = null) {
		val args = mutableMapOf<Any, Any?>(0.toByte() to component.id)
		if (listIndex != null) {
			args[41.toByte()] = listIndex
		}
		focusEvent.triggerEvent(args)
	}
}