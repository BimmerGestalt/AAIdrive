package me.hufman.androidautoidrive.carapp.carinfo.views

import com.soywiz.kds.getCyclic
import io.bimmergestalt.idriveconnectkit.rhmi.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.carapp.carinfo.CarDetailedInfo

class CategoryView(val state: RHMIState, val carInfo: CarDetailedInfo, val appSettings: AppSettings) {
	val listComponent = state.componentsList.filterIsInstance<RHMIComponent.List>().first()

	private val categories
		get() = if (!appSettings[AppSettings.KEYS.SHOW_ADVANCED_SETTINGS].toBoolean()) {
			carInfo.basicCategories
		} else {
			carInfo.advancedCategories
		}

	fun initWidgets() {
		// destination state is hardcoded, and it's not synced, so we just have to react
		listComponent.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index ->
			carInfo.category.tryEmit(categories.keys.toList().getCyclic(index))
		}

		state.focusCallback = FocusCallback { visible ->
			if (visible) {
				listComponent.getModel()?.value = RHMIModel.RaListModel.RHMIListAdapter(1, categories.keys.toList())
			}
		}
	}
}