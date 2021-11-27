package me.hufman.androidautoidrive.carapp.calendar.views

import io.bimmergestalt.idriveconnectkit.rhmi.FocusCallback
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIComponent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIModel
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import me.hufman.androidautoidrive.calendar.CalendarEvent
import me.hufman.androidautoidrive.carapp.L
import java.text.DateFormat
import java.util.*

class CalendarEventView(val state: RHMIState) {
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
					state.componentsList.filterIsInstance<RHMIComponent.Label>().isNotEmpty() &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().size >= 3
		}
	}

	var selectedEvent: CalendarEvent? = null

	val titleLabel: RHMIComponent.Label
	val timesList: RHMIComponent.List
	val locationList: RHMIComponent.List
	val descriptionList: RHMIComponent.List

	init {
		titleLabel = state.componentsList.filterIsInstance<RHMIComponent.Label>().first()
		timesList = state.componentsList.filterIsInstance<RHMIComponent.List>()[0]
		locationList = state.componentsList.filterIsInstance<RHMIComponent.List>()[1]
		descriptionList = state.componentsList.filterIsInstance<RHMIComponent.List>().last()
	}

	fun initWidgets() {
		state.focusCallback = FocusCallback { focused ->
			if (focused) {
				update()
			}
		}
	}

	fun update() {
		val event = selectedEvent
		titleLabel.getModel()?.asRaDataModel()?.value = event?.title ?: ""

		val times = RHMIModel.RaListModel.RHMIListConcrete(2).apply {
			if (event != null) {
				if (event.start[Calendar.HOUR_OF_DAY] == event.end[Calendar.HOUR_OF_DAY] &&
					event.start[Calendar.MINUTE] == event.end[Calendar.MINUTE]) {
					addRow(arrayOf(L.CALENDAR_TIME_DURATION, L.CALENDAR_TIME_ALLDAY))
				} else {
					val format = DateFormat.getInstance()
					addRow(arrayOf(L.CALENDAR_TIME_START, format.format(event.start.time)))
					addRow(arrayOf(L.CALENDAR_TIME_END, format.format(event.end.time)))
				}
			}
		}
		timesList.getModel()?.value = times

		val description = RHMIModel.RaListModel.RHMIListConcrete(1).apply {
			if (event?.description?.isNotBlank() == true) {
				addRow(arrayOf(event.description + "\n"))
			}
		}
		descriptionList.getModel()?.value = description
		descriptionList.setVisible(event?.description?.isNotBlank() == true)
		descriptionList.setSelectable(true)

		val location = RHMIModel.RaListModel.RHMIListConcrete(1).apply {
			if (event?.location?.isNotBlank() == true) {
				addRow(arrayOf(event.location + "\n"))
			}
		}
		locationList.getModel()?.value = location
		locationList.setVisible(event?.location?.isNotBlank() == true)
		locationList.setSelectable(true)

	}
}