package me.hufman.androidautoidrive.carapp.maps.views

import android.util.Log
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.carapp.maps.FrameUpdater
import me.hufman.androidautoidrive.carapp.maps.MapInteractionController
import me.hufman.idriveconnectionkit.rhmi.*

class MenuView(val state: RHMIState, val interaction: MapInteractionController, val frameUpdater: FrameUpdater) {
	companion object {
		val TAG = "MapMenu"
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
				state.componentsList.filterIsInstance<RHMIComponent.Label>().isNotEmpty() &&   // show whether currently navigating
				state.componentsList.filterIsInstance<RHMIComponent.List>().size > 1
		}
	}

	val menuEntries = listOf(L.MAP_ACTION_VIEWMAP, L.MAP_ACTION_SEARCH, L.MAP_ACTION_CLEARNAV)
	val rhmiMenuEntries = object: RHMIListAdapter<String>(3, menuEntries) {}
	val menuMap = state.componentsList.filterIsInstance<RHMIComponent.List>()[0]
	val mapModel = menuMap.getModel()!!
	val menuList = state.componentsList.filterIsInstance<RHMIComponent.List>()[1]

	fun initWidgets(stateMap: RHMIState, stateInput: RHMIState) {
		menuList.getModel()?.setValue(rhmiMenuEntries,0, menuEntries.size, menuEntries.size)
		state.componentsList.forEach {
			it.setVisible(false)
		}

		state.focusCallback = FocusCallback { focused ->
			if (focused) {
				Log.i(TAG, "Showing map on menu")
				frameUpdater.showWindow(350, 90, mapModel)
			} else {
				Log.i(TAG, "Hiding map on menu")
				frameUpdater.hideWindow(mapModel)
			}
		}

		menuMap.setVisible(true)
		menuMap.setSelectable(true)
		menuMap.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH.id, "350,0,*")
		menuMap.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = stateMap.id

		menuList.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH.id, "100,0,*")
		menuList.setVisible(true)
		menuList.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback {  listIndex ->
			val destStateId = when (listIndex) {
				0 -> stateMap.id    // must be index 0, because it's also index 0 in menuMap
				1 -> stateInput.id
				else -> state.id
			}
			Log.i(TAG, "User pressed menu item $listIndex ${menuEntries.getOrNull(listIndex)}, setting target ${menuList.getAction()?.asHMIAction()?.getTargetModel()?.id} to $destStateId")
			menuList.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = destStateId
			if (listIndex == 2) {
				// clear navigation
				interaction.stopNavigation()
			}
		}
		// it seems that menuMap and menuList share the same HMI Action values, so use the same RA handler
		menuMap.getAction()?.asRAAction()?.rhmiActionCallback = menuList.getAction()?.asRAAction()?.rhmiActionCallback
	}
}