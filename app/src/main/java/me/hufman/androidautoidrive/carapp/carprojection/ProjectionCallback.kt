package me.hufman.androidautoidrive.carapp.carprojection

import android.os.Binder
import me.hufman.carprojection.adapters.ICarProjectionCallbackService
import me.hufman.idriveconnectionkit.rhmi.RHMIEvent
import me.hufman.idriveconnectionkit.rhmi.RHMIState

class ProjectionCallback: ICarProjectionCallbackService() {
	var inputState: RHMIState? = null

	inner class LocalBinder: Binder() {
		fun getService(): ProjectionCallback {
			return this@ProjectionCallback
		}
	}

	fun openInput() {
		val state = inputState ?: return
		val app = inputState?.app ?: return
		app.events.values.filterIsInstance<RHMIEvent.FocusEvent>().firstOrNull()?.triggerEvent(mapOf(0 to state.id))
	}
}