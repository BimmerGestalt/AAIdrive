package me.hufman.androidautoidrive.carapp.carinfo.views

import io.bimmergestalt.idriveconnectkit.rhmi.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.batchDataTables
import me.hufman.androidautoidrive.carapp.carinfo.CarDetailedInfo
import me.hufman.androidautoidrive.carapp.rhmiDataTableFlow

/**
 * This state shows detailed car information as part of ReadoutApp
 * ReadoutApp's EntryButton is hardcoded to a specific RHMIState
 * so we must take whatever we are given
 */
class CarDetailedView(val state: RHMIState, val carInfo: CarDetailedInfo) {

	private val coroutineScope = CoroutineScope(Job() + Dispatchers.IO)
	private var updateJob: Job? = null

	private val label = state.componentsList.filterIsInstance<RHMIComponent.Button>().first()       // the only other widget that has a raDataModel
	private val list = state.componentsList.filterIsInstance<RHMIComponent.List>().first()

	private val fields: List<Flow<String>> = listOf(
			carInfo.engineTemp, carInfo.tempExterior,
			carInfo.oilTemp, carInfo.tempInterior,
			carInfo.batteryTemp, carInfo.tempExchanger,
			carInfo.fuelLevelLabel, carInfo.evLevelLabel,
			carInfo.accBatteryLevelLabel, carInfo.drivingGearLabel
	)

	fun initWidgets() {
		label.getModel()?.asRaDataModel()?.value = L.CARINFO_TITLE
		label.setSelectable(false)
		label.setEnabled(false)
		label.setVisible(true)
		list.setEnabled(true)
		list.setVisible(true)
		state.focusCallback = FocusCallback { visible ->
			if (visible) {
				onShow()
			} else {
				onHide()
			}
		}
		// this list has sync=false, so any click action can't be cancelled
	}

	private fun onShow() {
		updateJob?.cancel()

		updateJob = coroutineScope.launch {
			val rows: List<Flow<Array<Any>>> = fields.windowed(2, 2, true).map { cells ->
				if (cells.size == 2) {
					// combine waits for both sides to have a value, so we prepend a placeholder
					// gross! https://stackoverflow.com/a/72290550
					val preLeft = flowOf("").onCompletion { emitAll(cells[0]) }
					val preRight = flowOf("").onCompletion { emitAll(cells[1]) }
					preLeft.combine(preRight) { left, right ->
						arrayOf(left, right)
					}
				} else {
					cells[0].map { arrayOf(it, "") }
				}
			}
			rows.rhmiDataTableFlow { it }
					.batchDataTables(100)
					.collect {
						list.app.setModel(list.model, it)
					}
		}
	}
	private fun onHide() {
		updateJob?.cancel()
		updateJob = null
	}
}