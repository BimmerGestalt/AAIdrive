package me.hufman.androidautoidrive.carapp.notifications.views

import io.bimmergestalt.idriveconnectkit.rhmi.RHMIComponent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import me.hufman.androidautoidrive.carapp.L

class PermissionView(val state: RHMIState) {
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
					state.componentsList.filterIsInstance<RHMIComponent.Label>().isNotEmpty()
		}
	}

	val label: RHMIComponent.Label = state.componentsList.filterIsInstance<RHMIComponent.Label>().first()

	fun initWidgets() {
		label.setVisible(true)
		label.setEnabled(false)
		label.getModel()?.asRaDataModel()?.value = L.NOTIFICATION_PERMISSION_NEEDED
	}
}