package me.hufman.androidautoidrive.carapp

import android.util.Log
import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.carapp.maps.FrameUpdater
import me.hufman.idriveconnectionkit.rhmi.*

interface FullImageInteraction {
	fun navigateUp()
	fun navigateDown()
	fun click()
	fun getClickState(): RHMIState
}
class FullImageView(val state: RHMIState, val title: String, val interaction: FullImageInteraction, val frameUpdater: FrameUpdater, val getWidth: () -> Int, val getHeight: () -> Int) {
	companion object {
		val TAG = "FullImageView"
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
				state.componentsList.filterIsInstance<RHMIComponent.Image>().isNotEmpty() &&
				state.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty()
		}
	}

	val imageComponent = state.componentsList.filterIsInstance<RHMIComponent.Image>().first()
	val imageModel = imageComponent.getModel()!!
	val inputList = state.componentsList.filterIsInstance<RHMIComponent.List>().first()

	fun initWidgets() {
		// set up the components on the map
		state.getTextModel()?.asRaDataModel()?.value = title
		state.setProperty(24, 3)
		state.setProperty(26, "1,0,7")
		state.componentsList.forEach {
			it.setVisible(false)
		}

		state.focusCallback = FocusCallback { focused ->
			if (focused) {
				Log.i(TAG, "Showing map on full screen")
				imageComponent.setProperty(9, getWidth())
				imageComponent.setProperty(10, getHeight())
				frameUpdater.showWindow(getWidth(), getHeight(), imageModel)
			} else {
				Log.i(TAG, "Hiding map on full screen")
				frameUpdater.hideWindow(imageModel)
			}
		}

		val scrollList = RHMIModel.RaListModel.RHMIListConcrete(3)
		(0..2).forEach { scrollList.addRow(arrayOf("+", "", "")) }  // zoom in
		scrollList.addRow(arrayOf(title, "", "")) // neutral
		(4..6).forEach { scrollList.addRow(arrayOf("-", "", "")) }  // zoom out
		inputList.getModel()?.asRaListModel()?.setValue(scrollList, 0, scrollList.height, scrollList.height)
		state.app.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first().triggerEvent(mapOf(0 to inputList.id, 41 to 3))

		inputList.getSelectAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { listIndex ->
			if (listIndex in 0..2) {   // each wheel click through the list will trigger another step of 1
				interaction.navigateUp()
			}
			if (listIndex in 4..6) {
				interaction.navigateDown()
			}
			state.app.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first().triggerEvent(mapOf(0 to inputList.id, 41 to 3))  // set focus to the middle of the list
		}
		inputList.getAction()?.asRAAction()?.rhmiActionCallback = object: RHMIActionButtonCallback {
			override fun onAction(invokedBy: Int?) {
				// get the state to click to
				val target = if (invokedBy == 2) {   // bookmark event
					state.id
				} else {
					interaction.getClickState().id
				}

				// set the next state and try viewing it
				try {
					state.app.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first().triggerEvent(mapOf(0 to target))
				} catch (e: BMWRemoting.IllegalArgumentException) {}

				if (invokedBy != 2) {
					// do any other click behaviors
					interaction.click()
				}
			}
		}
		inputList.setVisible(true)
		inputList.setProperty(20, 50000)  // positionX, so that we don't see it but should still be interacting with it
		inputList.setProperty(21, 50000)  // positionY, so that we don't see it but should still be interacting with it
		inputList.setProperty(22, true)

		imageComponent.setVisible(true)
		imageComponent.setProperty(18, 0)    // offsetX
		imageComponent.setProperty(19, 0)    // offsetY
		imageComponent.setProperty(20, -78)    // positionX -16
		imageComponent.setProperty(21, -80)    // positionY 0
		imageComponent.setProperty(9, getWidth())
		imageComponent.setProperty(10, getHeight())
	}
}