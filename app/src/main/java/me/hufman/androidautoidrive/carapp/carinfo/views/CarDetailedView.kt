package me.hufman.androidautoidrive.carapp.carinfo.views

import io.bimmergestalt.idriveconnectkit.rhmi.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.hufman.androidautoidrive.CarThreadExceptionHandler
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.batchDataTables
import me.hufman.androidautoidrive.carapp.carinfo.CarDetailedInfo
import me.hufman.androidautoidrive.carapp.rhmiDataTableFlow
import kotlin.coroutines.CoroutineContext

/**
 * This state shows detailed car information as part of ReadoutApp
 * ReadoutApp's EntryButton is hardcoded to a specific RHMIState
 * so we must take whatever we are given
 */
class CarDetailedView(val state: RHMIState, val coroutineContext: CoroutineContext, val carInfo: CarDetailedInfo) {
	val coroutineScope = CoroutineScope(coroutineContext + CarThreadExceptionHandler)
	private val label = state.componentsList.filterIsInstance<RHMIComponent.Button>().first()       // the only other widget that has a raDataModel
	private val list = state.componentsList.filterIsInstance<RHMIComponent.List>().first()

	fun initWidgets() {
		label.getModel()?.asRaDataModel()?.value = L.CARINFO_TITLE
		val categoriesEnabled = carInfo.categories.size > 1
		label.setSelectable(categoriesEnabled)
		label.setEnabled(categoriesEnabled)
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
		coroutineScope.launch {
			carInfo.category.collectLatest { title ->
				label.getModel()?.asRaDataModel()?.value = title
			}
		}
		coroutineScope.launch {
			carInfo.categoryFields.collectLatest { fields ->
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
	}
	private fun onHide() {
		coroutineScope.coroutineContext.cancelChildren()
	}
}