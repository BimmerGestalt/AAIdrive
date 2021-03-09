package me.hufman.androidautoidrive.carapp.notifications.views

import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import me.hufman.idriveconnectionkit.rhmi.RHMIState

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