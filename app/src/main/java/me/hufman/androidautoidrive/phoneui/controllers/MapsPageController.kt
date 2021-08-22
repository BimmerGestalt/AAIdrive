package me.hufman.androidautoidrive.phoneui.controllers

import me.hufman.androidautoidrive.phoneui.viewmodels.MapSettingsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.PermissionsModel

class MapsPageController(val mapSettingsModel: MapSettingsModel,
                         val permissionsModel: PermissionsModel,
                         val permissionsController: PermissionsController) {

	fun onChangedSwitchGMaps(isChecked: Boolean) {
		mapSettingsModel.mapEnabled.setValue(isChecked)
		if (isChecked) {
			// make sure we have permissions to show current location
			if (permissionsModel.hasLocationPermission.value != true) {
				permissionsController.promptLocation()
			}
		}
	}
}