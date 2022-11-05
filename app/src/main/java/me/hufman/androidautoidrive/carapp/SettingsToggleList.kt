package me.hufman.androidautoidrive.carapp

import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIActionListCallback
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIComponent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIModel
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIProperty
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettings

class SettingsToggleList(val component: RHMIComponent.List, val appSettings: MutableAppSettings, val settings: List<AppSettings.KEYS>, val checkmarkImageId: Int = 150) {

	val menuSettingsListData = object: RHMIModel.RaListModel.RHMIListAdapter<AppSettings.KEYS>(3, settings) {
		override fun convertRow(index: Int, item: AppSettings.KEYS): Array<Any> {
			val checkmark = if (isChecked(item)) BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, checkmarkImageId) else ""
			val name = settingName(item)
			return arrayOf(checkmark, "", name)
		}

		fun isChecked(setting: AppSettings.KEYS): Boolean {
			return appSettings[setting].toBoolean()
		}
		fun settingName(setting: AppSettings.KEYS): String {
			return when (setting) {
				AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP -> L.NOTIFICATION_POPUPS
				AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER -> L.NOTIFICATION_POPUPS_PASSENGER
				AppSettings.KEYS.NOTIFICATIONS_SOUND -> L.NOTIFICATION_SOUND
				AppSettings.KEYS.NOTIFICATIONS_READOUT -> L.NOTIFICATION_READOUT
				AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP -> L.NOTIFICATION_READOUT_POPUP
				AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER -> L.NOTIFICATION_READOUT_POPUP_PASSENGER
				AppSettings.KEYS.MAP_WIDESCREEN -> L.MAP_WIDESCREEN
				AppSettings.KEYS.MAP_INVERT_SCROLL -> L.MAP_INVERT_ZOOM
				AppSettings.KEYS.MAP_TRAFFIC -> L.MAP_TRAFFIC
				AppSettings.KEYS.MAP_SATELLITE-> L.MAP_SATELLITE
				AppSettings.KEYS.MAP_BUILDINGS -> L.MAP_BUILDINGS
				AppSettings.KEYS.MAP_TILT -> L.MAP_TILT
				AppSettings.KEYS.MAP_CUSTOM_STYLE -> L.MAP_CUSTOM_STYLE
				else -> ""
			}
		}
	}

	fun initWidgets() {
		component.setVisible(true)
		component.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH.id, "55,0,*")
		component.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index ->
			val setting = settings.getOrNull(index)
			if (setting != null) {
				appSettings[setting] = (!appSettings[setting].toBoolean()).toString()
			}
			redraw()
			throw RHMIActionAbort()
		}
	}

	fun redraw() {
		component.getModel()?.value = menuSettingsListData
	}
}