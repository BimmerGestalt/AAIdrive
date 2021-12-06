package me.hufman.androidautoidrive.carapp.calendar.views

import android.location.Address
import io.bimmergestalt.idriveconnectkit.rhmi.*
import me.hufman.androidautoidrive.calendar.CalendarEvent
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.navigation.AddressSearcher
import me.hufman.androidautoidrive.carapp.navigation.NavigationParser
import me.hufman.androidautoidrive.carapp.navigation.NavigationTrigger
import java.text.DateFormat
import java.util.*

class CalendarEventView(val state: RHMIState, val addressSearcher: AddressSearcher, val navigationTrigger: NavigationTrigger) {
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
					state.componentsList.filterIsInstance<RHMIComponent.Label>().isNotEmpty() &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().size >= 3
		}
	}

	var selectedEvent: CalendarEvent? = null
	var address: Address? = null

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
		timesList.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
			val address = this.address
			if (address != null) {
				navigationTrigger.triggerNavigation(NavigationParser.addressToRHMI(address))
			}
		}
		locationList.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
			// for some reason this doesn't seem to ever trigger from the car
			val address = this.address
			if (address != null) {
				navigationTrigger.triggerNavigation(NavigationParser.addressToRHMI(address))
			}
		}
	}

	fun update() {
		val event = selectedEvent
		titleLabel.getModel()?.asRaDataModel()?.value = event?.title ?: ""

		updateTimesList()

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
		locationList.setEnabled(false)

		this.address = null
		if (event?.location?.isNotBlank() == true) {
			val address = addressSearcher.search(event.location)
			if (address != null) {
				locationList.setEnabled(true)
				this.address = address
				updateTimesList()
			}
		}
	}

	private fun updateTimesList() {
		val event = selectedEvent
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
			if (address != null) {
				addRow(arrayOf(L.CALENDAR_NAVIGATE))
			}
		}
		timesList.getModel()?.value = times
		timesList.setSelectable(true)

	}
}