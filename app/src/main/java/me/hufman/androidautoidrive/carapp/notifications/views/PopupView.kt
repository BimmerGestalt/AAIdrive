package me.hufman.androidautoidrive.carapp.notifications.views

import android.util.Log
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.notifications.CarNotification
import me.hufman.androidautoidrive.carapp.notifications.PopupHistory
import me.hufman.androidautoidrive.carapp.notifications.TAG
import me.hufman.idriveconnectionkit.rhmi.*
import me.hufman.idriveconnectionkit.rhmi.mocking.RHMIApplicationMock

class PopupView(val state: RHMIState, val phoneAppResources: PhoneAppResources) {
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
	val focusEvent: RHMIEvent.FocusEvent?

	var currentNotification: CarNotification? = null

	init {
		val dummyLabel = RHMIModel.RaDataModel(RHMIApplicationMock(), 0)
		titleLabel = state.getTextModel()?.asRaDataModel() ?: dummyLabel
		bodyLabel1 = state.componentsList.filterIsInstance<RHMIComponent.Label>().firstOrNull()?.getModel()?.asRaDataModel() ?: dummyLabel
		bodyLabel2 = state.componentsList.filterIsInstance<RHMIComponent.Label>().lastOrNull()?.getModel()?.asRaDataModel() ?: dummyLabel
		popEvent = state.app.events.values.filterIsInstance<RHMIEvent.PopupEvent>().firstOrNull { it.getTarget() == state }
		focusEvent = state.app.events.values.filterIsInstance<RHMIEvent.FocusEvent>().firstOrNull()
	}

	fun initWidgets() {
		state.componentsList.filterIsInstance<RHMIComponent.Label>().lastOrNull()?.setSelectable(true)
	}

	fun showNotification(sbn: CarNotification) {
		currentNotification = sbn

		try {
			val appname = phoneAppResources.getAppName(sbn.packageName)
			titleLabel.value = appname
			bodyLabel1.value = sbn.title
			bodyLabel2.value = sbn.text.trim().split(Regex("\n")).lastOrNull() ?: ""
			popEvent?.triggerEvent(mapOf(0 to true))
		} catch (e: Exception) {
			Log.e(TAG, "Error while triggering notification popup: $e")
		}
	}

	fun hideNotification() {
		try {
			popEvent?.triggerEvent(mapOf(0 to false))
		} catch (e: Exception) {
			Log.e(TAG, "Error while hiding notification popup: $e")
		}
	}
}