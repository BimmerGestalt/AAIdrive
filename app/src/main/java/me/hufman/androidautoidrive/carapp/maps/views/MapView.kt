package me.hufman.androidautoidrive.carapp.maps.views

import android.util.Log
import me.hufman.androidautoidrive.carapp.maps.FrameUpdater
import me.hufman.androidautoidrive.carapp.maps.MapApp.Companion.MAX_HEIGHT
import me.hufman.androidautoidrive.carapp.maps.MapApp.Companion.MAX_WIDTH
import me.hufman.androidautoidrive.carapp.maps.MapInteractionController
import me.hufman.idriveconnectionkit.rhmi.*

class MapView(val state: RHMIState, val interaction: MapInteractionController, val frameUpdater: FrameUpdater, val getWidth: () -> Int, val getHeight: () -> Int) {
	companion object {
		val TAG = "MapView"
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
				state.componentsList.filterIsInstance<RHMIComponent.Image>().isNotEmpty() &&
				state.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty()
		}
	}

	val mapImage = state.componentsList.filterIsInstance<RHMIComponent.Image>().first()
	val mapModel = mapImage.getModel()!!
	val mapInputList = state.componentsList.filterIsInstance<RHMIComponent.List>().first()

	fun initWidgets(stateMenu: RHMIState) {
		// set up the components on the map
		state.setProperty(24, 3)
		state.setProperty(26, "1,0,7")
		state.componentsList.forEach {
			it.setVisible(false)
		}

		state.focusCallback = FocusCallback { focused ->
			if (focused) {
				Log.i(TAG, "Showing map on full screen")
				frameUpdater.showWindow(getWidth(), getHeight(), mapModel)
			} else {
				Log.i(TAG, "Hiding map on full screen")
				frameUpdater.hideWindow(mapModel)
			}
		}

		val scrollList = RHMIModel.RaListModel.RHMIListConcrete(3)
		(0..2).forEach { scrollList.addRow(arrayOf("+", "", "")) }  // zoom in
		scrollList.addRow(arrayOf("Map", "", "")) // neutral
		(4..6).forEach { scrollList.addRow(arrayOf("-", "", "")) }  // zoom out
		mapInputList.getModel()?.asRaListModel()?.setValue(scrollList, 0, scrollList.height, scrollList.height)
		state.app.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first().triggerEvent(mapOf(0 to mapInputList.id, 41 to 3))

		mapInputList.getSelectAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { listIndex ->
			if (listIndex in 0..2) {
				interaction.zoomIn(1)   // each wheel click through the list will trigger another step of 1
			}
			if (listIndex in 4..6) {
				interaction.zoomOut(1)
			}
			state.app.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first().triggerEvent(mapOf(0 to mapInputList.id, 41 to 3))  // set focus to the middle of the list
		}
		mapInputList.getAction()?.asRAAction()?.rhmiActionCallback = object: RHMIActionButtonCallback {
			override fun onAction(invokedBy: Int?) {
				val destState = if (invokedBy == 2) {   // bookmark event
					state.id
				} else {
					stateMenu.id
				}
				state.app.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first().triggerEvent(mapOf(0 to destState))
			}
		}
		mapInputList.setVisible(true)
		mapInputList.setProperty(20, 50000)  // positionX, so that we don't see it but should still be interacting with it
		mapInputList.setProperty(21, 50000)  // positionY, so that we don't see it but should still be interacting with it
		mapInputList.setProperty(22, true)

		mapImage.setVisible(true)
		mapImage.setProperty(20, -16)    // positionX
		mapImage.setProperty(21, 0)    // positionY
		mapImage.setProperty(9, MAX_WIDTH)
		mapImage.setProperty(10, MAX_HEIGHT)
	}
}