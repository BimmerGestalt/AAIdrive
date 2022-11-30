package me.hufman.androidautoidrive.carapp.notifications.views

import android.util.Log
import io.bimmergestalt.idriveconnectkit.rhmi.*
import io.bimmergestalt.idriveconnectkit.rhmi.mocking.RHMIApplicationMock
import me.hufman.androidautoidrive.carapp.notifications.TAG
import me.hufman.androidautoidrive.notifications.CarNotification
import me.hufman.androidautoidrive.utils.GraphicsHelpers

/**
 * Represents the PopupState from BMW One
 */
class ID5PopupView(val state: RHMIState, val graphicsHelpers: GraphicsHelpers, val imageId: Int): PopupView {
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PopupState &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty()
		}
	}

	val titleLabel: RHMIModel.RaDataModel
	val bodyList: RHMIComponent.List
	val openButton: RHMIComponent.Button
	val popEvent: RHMIEvent.PopupEvent?

	var onClicked: ((CarNotification) -> Unit)? = null
	override var currentNotification: CarNotification? = null

	init {
		val dummyLabel = RHMIModel.RaDataModel(RHMIApplicationMock(), 0)
		titleLabel = state.getTextModel()?.asRaDataModel() ?: dummyLabel
		bodyList = state.componentsList.filterIsInstance<RHMIComponent.List>().first()
		openButton = state.componentsList.filterIsInstance<RHMIComponent.Button>().first()
		popEvent = state.app.events.values.filterIsInstance<RHMIEvent.PopupEvent>().firstOrNull { it.getTarget() == state }
	}

	override fun initWidgets() {
		// only show list and a single button
		state.componentsList.forEach {
			it.setVisible(false)
		}

		bodyList.setVisible(true)
		openButton.setVisible(true)
		openButton.getImageModel()?.asImageIdModel()?.imageId = imageId

		// make the button jump to the DetailView
		openButton.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionCallback {
			currentNotification?.also { sbn ->
				onClicked?.invoke(sbn)
			}
		}

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
			val listData = RHMIModel.RaListModel.RHMIListConcrete(9)
			val icon = sbn.icon?.let { graphicsHelpers.compress(it, 48, 48) } ?: ""
			listData.addRow(arrayOf(icon, sbn.title, sbn.lastLine, "",
					/*TextMeeting*/"", "", /*TextArrival*/"", /*ColorMeeting*/"", /*ColorArrival*/""))
			bodyList.getModel()?.value = listData
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