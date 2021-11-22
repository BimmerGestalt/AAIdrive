package me.hufman.androidautoidrive.carapp.calendar.views

import io.bimmergestalt.idriveconnectkit.rhmi.RHMIComponent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIModel
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import me.hufman.androidautoidrive.carapp.L

class PermissionView(val state: RHMIState) {
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
					state.componentsList.size == 1 &&
					state.componentsList.filterIsInstance<RHMIComponent.Label>().size == 1
		}
	}

	val label: RHMIComponent.Label = state.componentsList.filterIsInstance<RHMIComponent.Label>().first()
}