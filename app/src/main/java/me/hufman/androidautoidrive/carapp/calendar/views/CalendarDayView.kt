package me.hufman.androidautoidrive.carapp.calendar.views

import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.Utils.etchAsInt
import io.bimmergestalt.idriveconnectkit.rhmi.*
import me.hufman.androidautoidrive.calendar.CalendarEvent
import me.hufman.androidautoidrive.calendar.CalendarProvider
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.calendar.RHMIDateUtils
import java.util.*

class CalendarDayView(val state: RHMIState, val calendarProvider: CalendarProvider) {
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.CalendarState
		}
	}

	var selectedDate: Calendar? = null
	var events: List<CalendarEvent> = ArrayList()
	val calendarDay: RHMIComponent.CalendarDay
	val dateModel: RHMIModel.RaIntModel
	val listModel: RHMIModel.RaListModel

	init {
		state as RHMIState.CalendarState
		calendarDay = state.componentsList.filterIsInstance<RHMIComponent.CalendarDay>().first()
		dateModel = calendarDay.getDateModel()?.asRaIntModel()!!
		listModel = calendarDay.getAppointmentListModel()?.asRaListModel()!!
	}

	fun initWidgets(eventView: CalendarEventView) {
		state.focusCallback = FocusCallback { focused ->
			if (focused) {
				update()
			}
		}
		calendarDay.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionCallback { args ->
			val index = etchAsInt(args?.get(0.toByte()) ?: -1)
			if (index == 0) {
				// the header row, showing the current date
				selectedDate?.add(Calendar.DAY_OF_YEAR, 1)
				update()
				throw RHMIActionAbort()
			} else if (index > 0) {
				// car indexes the events list starting at 1
				val event = events.getOrNull(index - 1)
				eventView.selectedEvent = event
				calendarDay.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = eventView.state.id
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
			// need to select the entire month to find long-running events around the current day
			val events = calendarProvider.getEvents(currentDate[Calendar.YEAR], currentDate[Calendar.MONTH] + 1, null).filter {
				it.start[Calendar.DAY_OF_MONTH] == currentDate[Calendar.DAY_OF_MONTH]
			}
//			this.events = events.sortedBy {
//				(it.start[Calendar.HOUR_OF_DAY] * 60 + it.start[Calendar.MINUTE]) * 1440 +
//				it.end[Calendar.HOUR_OF_DAY] * 60 + it.end[Calendar.MINUTE]
//			}
			this.events = events
			val carList = object: RHMIModel.RaListModel.RHMIListAdapter<CalendarEvent>(4, events) {
				override fun convertRow(index: Int, item: CalendarEvent): Array<Any> {
					return arrayOf(
						RHMIDateUtils.convertToRhmiTime(item.start),
						RHMIDateUtils.convertToRhmiTime(item.end),
						BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, 0),
						item.title
					)
				}
			}
			listModel.value = carList
		}
	}
}