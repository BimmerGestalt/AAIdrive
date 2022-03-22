package me.hufman.androidautoidrive.carapp.maps.views

import android.util.Log
import io.bimmergestalt.idriveconnectkit.rhmi.*
import kotlinx.coroutines.runBlocking
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.StoredList
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.SettingsToggleList
import me.hufman.androidautoidrive.carapp.maps.FrameUpdater
import me.hufman.androidautoidrive.carapp.maps.MapAppMode
import me.hufman.androidautoidrive.carapp.maps.MapInteractionController
import me.hufman.androidautoidrive.maps.MapPlaceSearch

class MenuView(val state: RHMIState, val interaction: MapInteractionController, val mapPlaceSearch: MapPlaceSearch, val frameUpdater: FrameUpdater, val mapAppMode: MapAppMode) {
	companion object {
		val TAG = "MapMenu"
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
				state.componentsList.filterIsInstance<RHMIComponent.Label>().isNotEmpty() &&   // show whether currently navigating
				state.componentsList.filterIsInstance<RHMIComponent.List>().size > 3
		}
	}

	val alwaysMenuEntries = listOf(L.MAP_ACTION_VIEWMAP, L.MAP_ACTION_SEARCH)
	val duringNavMenuEntries = listOf(L.MAP_ACTION_RECALC_NAV, L.MAP_ACTION_CLEARNAV)
	val menuEntries = ArrayList<String>()
	val rhmiMenuEntries = object: RHMIModel.RaListModel.RHMIListAdapter<String>(3, menuEntries) {}
	val menuMap = state.componentsList.filterIsInstance<RHMIComponent.List>()[0]
	val mapModel = menuMap.getModel()!!
	val menuList = state.componentsList.filterIsInstance<RHMIComponent.List>()[1]

	val labelDestinations: RHMIComponent.Label
	val menuDestinations = state.componentsList.filterIsInstance<RHMIComponent.List>()[2]
	val destinationEntries = StoredList(mapAppMode.appSettings, AppSettings.KEYS.MAP_QUICK_DESTINATIONS)
	val rhmiDestinationEntries = object: RHMIModel.RaListModel.RHMIListAdapter<String>(3, destinationEntries) {}

	val labelSettings: RHMIComponent.Label
	val menuSettings = state.componentsList.filterIsInstance<RHMIComponent.List>()[3]
	val settingsView: SettingsToggleList = SettingsToggleList(menuSettings, mapAppMode.appSettings, mapAppMode.settings, 149)

	init {
		val destinationsListIndex = state.componentsList.indexOf(menuDestinations)
		labelDestinations = state.componentsList.filterIndexed { index, rhmiComponent ->
			index < destinationsListIndex && rhmiComponent is RHMIComponent.Label
		}.filterIsInstance<RHMIComponent.Label>().last()

		val settingsListIndex = state.componentsList.indexOf(menuSettings)
		labelSettings = state.componentsList.filterIndexed { index, rhmiComponent ->
			index < settingsListIndex && rhmiComponent is RHMIComponent.Label
		}.filterIsInstance<RHMIComponent.Label>().last()
	}
	fun initWidgets(stateMap: RHMIState, stateInput: RHMIState) {
		mapAppMode.appSettings.callback = {
			redrawDestinations()
			settingsView.redraw()
		}
		state.componentsList.forEach {
			it.setVisible(false)
		}
		redrawCommands()

		state.focusCallback = FocusCallback { focused ->
			if (focused) {
				redrawCommands()
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
		menuList.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { listIndex ->
			val destStateId = when (listIndex) {
				0 -> stateMap.id    // must be index 0, because it's also index 0 in menuMap
				1 -> stateInput.id
				else -> state.id
			}
			Log.i(TAG, "User pressed menu item $listIndex ${menuEntries.getOrNull(listIndex)}, setting target ${menuList.getAction()?.asHMIAction()?.getTargetModel()?.id} to $destStateId")
			menuList.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = destStateId
			if (listIndex== 2) {
				// recalculate nav
				interaction.recalcNavigation()
			}
			if (listIndex == 3) {
				// clear navigation
				interaction.stopNavigation()
				// the interaction is async, but we trust that it will clear the destination so we can redraw to hide the commands
				mapAppMode.currentNavDestination = null
				redrawCommands()
			}
		}
		// it seems that menuMap and menuList share the same HMI Action values, so use the same RA handler
		menuMap.getAction()?.asRAAction()?.rhmiActionCallback = menuList.getAction()?.asRAAction()?.rhmiActionCallback

		labelDestinations.getModel()?.asRaDataModel()?.value = L.MAP_DESTINATIONS
		menuDestinations.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH.id, "55,0,*")
		menuDestinations.setVisible(true)
		menuDestinations.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { listIndex ->
			runBlocking {
				val destination = destinationEntries.getOrNull(listIndex)?.let {
					mapPlaceSearch.searchLocationsAsync(it).await().getOrNull(0)
				}
				val locationResult = if (destination != null && destination.location == null) {
					mapPlaceSearch.resultInformationAsync(destination.id).await()    // ask for LatLong, to navigate to
				} else {
					destination
				}
				if (locationResult?.location == null) {
					throw RHMIActionAbort()
				}
				menuDestinations.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = stateMap.id
				interaction.navigateTo(locationResult.location)
			}
		}

		// decorate the settings
		labelSettings.setVisible(true)
		labelSettings.getModel()?.asRaDataModel()?.value = L.MAP_OPTIONS

		settingsView.initWidgets()
	}

	private fun redrawCommands() {
		menuEntries.clear()
		menuEntries.addAll(alwaysMenuEntries)
		if (mapAppMode.currentNavDestination != null) {
			menuEntries.addAll(duringNavMenuEntries)
		}
		menuList.getModel()?.value = rhmiMenuEntries
	}

	private fun redrawDestinations() {
		labelDestinations.setVisible(rhmiDestinationEntries.height > 0)
		menuDestinations.getModel()?.value = rhmiDestinationEntries
	}
}