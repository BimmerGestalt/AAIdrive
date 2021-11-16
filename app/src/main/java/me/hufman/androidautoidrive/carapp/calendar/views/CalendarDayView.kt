package me.hufman.androidautoidrive.carapp.calendar.views

import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.rhmi.FocusCallback
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIComponent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIModel
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import me.hufman.androidautoidrive.calendar.CalendarEvent
import me.hufman.androidautoidrive.calendar.CalendarProvider
import me.hufman.androidautoidrive.carapp.calendar.RHMIDateUtils
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class CalendarDayView(val state: RHMIState, val calendarProvider: CalendarProvider) {
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.CalendarState
		}
	}

	var selectedDate: Calendar? = null
	val dateModel: RHMIModel.RaIntModel
	val listModel: RHMIModel.RaListModel

	init {
		state as RHMIState.CalendarState
		val calendarDay = state.componentsList.filterIsInstance<RHMIComponent.CalendarDay>().first()
		dateModel = calendarDay.getDateModel()?.asRaIntModel()!!
		listModel = calendarDay.getAppointmentListModel()?.asRaListModel()!!
	}

	fun initWidgets() {
		state.focusCallback = FocusCallback { focused ->
			if (focused) {
				update()
			}
		}
	}

	fun update() {
		val currentDate = selectedDate
		if (currentDate != null) {
//			val date = currentDate.time
//			val label = DateFormat.getDateInstance().format(date)
//			state.getTextModel()?.asRaDataModel()?.value = label
			dateModel.value = RHMIDateUtils.convertToRhmiDate(currentDate)
		} else {
			state.getTextModel()?.asRaDataModel()?.value = ""
		}

		if (currentDate != null) {
			val events = calendarProvider.getEvents(currentDate.get(Calendar.YEAR), currentDate.get(Calendar.MONTH) + 1, currentDate.get(Calendar.DAY_OF_MONTH))
			val carList = object: RHMIModel.RaListModel.RHMIListAdapter<CalendarEvent>(4, events) {
				override fun convertRow(index: Int, item: CalendarEvent): Array<Any> {
					return arrayOf(
						RHMIDateUtils.convertToRhmiTime(item.start),
						RHMIDateUtils.convertToRhmiTime(item.end),
						BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, 38),
						item.title
					)
				}
			}
			listModel.value = carList
		}
	}
}