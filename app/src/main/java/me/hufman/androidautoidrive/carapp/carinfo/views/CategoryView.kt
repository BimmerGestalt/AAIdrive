package me.hufman.androidautoidrive.carapp.carinfo.views

import com.soywiz.kds.getCyclic
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIActionListCallback
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIComponent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIModel
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import me.hufman.androidautoidrive.carapp.carinfo.CarDetailedInfo

class CategoryView(val state: RHMIState, val carInfo: CarDetailedInfo) {
	val listComponent = state.componentsList.filterIsInstance<RHMIComponent.List>().first()

	fun initWidgets() {
		// destination state is hardcoded, and it's not synced, so we just have to react
		listComponent.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index ->
			carInfo.category.tryEmit(carInfo.categories.keys.toList().getCyclic(index))
		}

		val listContents = RHMIModel.RaListModel.RHMIListAdapter<String>(1, carInfo.categories.keys.toList())
		listComponent.getModel()?.value = listContents
	}
}