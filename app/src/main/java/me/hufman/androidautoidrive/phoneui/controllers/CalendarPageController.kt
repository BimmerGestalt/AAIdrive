package me.hufman.androidautoidrive.phoneui.controllers

import me.hufman.androidautoidrive.phoneui.viewmodels.CalendarSettingsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.PermissionsModel

class CalendarPageController(val calendarSettingsModel: CalendarSettingsModel,
                             val permissionsModel: PermissionsModel,
                             val permissionsController: PermissionsController) {

	fun onChangedSwitchCalendar(isChecked: Boolean) {
		calendarSettingsModel.calendarEnabled.setValue(isChecked)
		if (isChecked) {
			// make sure we have permissions to read the notifications
			if (permissionsModel.hasCalendarPermission.value != true) {
				permissionsController.promptCalendar()
			}
		}
	}
}