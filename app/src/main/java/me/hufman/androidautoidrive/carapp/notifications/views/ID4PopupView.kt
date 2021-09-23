package me.hufman.androidautoidrive.carapp.notifications.views

import android.util.Log
import io.bimmergestalt.idriveconnectkit.rhmi.*
import io.bimmergestalt.idriveconnectkit.rhmi.mocking.RHMIApplicationMock
import me.hufman.androidautoidrive.carapp.notifications.TAG
import me.hufman.androidautoidrive.notifications.CarNotification

class ID4PopupView(val state: RHMIState): PopupView {
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PopupState &&
					state.componentsList.filterIsInstance<RHMIComponent.Label>().isNotEmpty()
		}
	}

	val titleLabel: RHMIModel.RaDataModel
	val bodyLabel1: RHMIModel.RaDataModel
	val bodyLabel2: RHMIModel.RaDataModel
	val popEvent: RHMIEvent.PopupEvent?

	override var currentNotification: CarNotification? = null

	init {
		val dummyLabel = RHMIModel.RaDataModel(RHMIApplicationMock(), 0)
		titleLabel = state.getTextModel()?.asRaDataModel() ?: dummyLabel
		bodyLabel1 = state.componentsList.filterIsInstance<RHMIComponent.Label>().firstOrNull()?.getModel()?.asRaDataModel() ?: dummyLabel
		bodyLabel2 = state.componentsList.filterIsInstance<RHMIComponent.Label>().lastOrNull()?.getModel()?.asRaDataModel() ?: dummyLabel
		popEvent = state.app.events.values.filterIsInstance<RHMIEvent.PopupEvent>().firstOrNull { it.getTarget() == state }
	}

	override fun initWidgets() {
		state.componentsList.filterIsInstance<RHMIComponent.Label>().lastOrNull()?.setSelectable(true)

		state.focusCallback = FocusCallback { focused ->
			if (!focused) {
				currentNotification = null
			}
		}
	}

	override fun showNotification(sbn: CarNotification) {
		currentNotification = sbn

		try {
			titleLabel.value = sbn.appName
			bodyLabel1.value = sbn.title
			bodyLabel2.value = sbn.lastLine
			popEvent?.triggerEvent(mapOf(0 to true))
		} catch (e: Exception) {
			Log.e(TAG, "Error while triggering notification popup: $e")
			currentNotification = null
		}
	}

	override fun hideNotification() {
		try {
			popEvent?.triggerEvent(mapOf(0 to false))
		} catch (e: Exception) {
			Log.e(TAG, "Error while hiding notification popup: $e")
		}
	}
}