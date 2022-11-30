package me.hufman.androidautoidrive.carapp.calendar.views

import io.bimmergestalt.idriveconnectkit.Utils.etchAsInt
import io.bimmergestalt.idriveconnectkit.rhmi.FocusCallback
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIActionCallback
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIModel
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import me.hufman.androidautoidrive.calendar.CalendarProvider
import me.hufman.androidautoidrive.carapp.FocusTriggerController
import me.hufman.androidautoidrive.carapp.calendar.RHMIDateUtils
import java.util.*

class CalendarMonthView(val state: RHMIState, val focusTriggerController: FocusTriggerController, val calendarProvider: CalendarProvider) {
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.CalendarMonthState
		}
	}

	var shownPermission = false
	var selectedDate: Calendar = Calendar.getInstance()
	val dateModel: RHMIModel.RaIntModel
	val listModel: RHMIModel.RaListModel

	init {
		state as RHMIState.CalendarMonthState
		dateModel = state.getDateModel() as RHMIModel.RaIntModel
		listModel = state.getHighlightListModel() as RHMIModel.RaListModel
	}

	fun initWidgets(permissionView: PermissionView, calendarDayView: CalendarDayView) {
		state as RHMIState.CalendarMonthState
		state.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionCallback { args ->
			// user clicked a day
			val carDate = etchAsInt(args?.get(0.toByte()))
			calendarDayView.selectedDate = RHMIDateUtils.convertFromRhmiDate(carDate)
			// tell the car to go back to this date when returning
			selectedDate = RHMIDateUtils.convertFromRhmiDate(carDate)
			dateModel.value = RHMIDateUtils.convertToRhmiDate(selectedDate)
			// open the day view
			state.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = calendarDayView.state.id
		}
		state.getChangeAction()?.asRAAction()?.rhmiActionCallback = RHMIActionCallback { args ->
			// car is showing a different month
			val carDate = etchAsInt(args?.get(0.toByte()))
			selectedDate = RHMIDateUtils.convertFromRhmiDate(carDate)
//			dateModel.value = RHMIDateUtils.convertToRhmiDate(selectedDate)
			update()
		}

		state.focusCallback = FocusCallback { focused ->
			if (focused) {
				if (!calendarProvider.hasPermission() && !shownPermission) {
					focusTriggerController.focusState(permissionView.state, false)
					shownPermission = true
				} else {
					update()
				}
			}
		}
	}

	fun update() {
		val currentDate = selectedDate
		if (!calendarProvider.hasPermission()) {
			return
		}

		dateModel.value = RHMIDateUtils.convertToRhmiDate(selectedDate)
		val events = calendarProvider.getEvents(currentDate[Calendar.YEAR], currentDate[Calendar.MONTH] + 1, null)
		val highlightedDays = HashSet<Int>()
		events.forEach {
			highlightedDays.add(it.start[Calendar.DAY_OF_MONTH])
		}
		val highlightedDaysList = RHMIModel.RaListModel.RHMIListConcrete(1)
		highlightedDays.forEach {
			highlightedDaysList.addRow(arrayOf(it))
		}
//		events.forEach {
//			highlightedDaysList.addRow(arrayOf(it.start[Calendar.DAY_OF_MONTH]))
//		}

		// if the user hasn't changed the view yet, update
		if (selectedDate[Calendar.YEAR] == currentDate[Calendar.YEAR] &&
			selectedDate[Calendar.MONTH] == currentDate[Calendar.MONTH]) {
			listModel.value = highlightedDaysList
		}
	}
}