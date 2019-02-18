package me.hufman.androidautoidrive.carapp.music.views

import android.util.Log
import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.Utils
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.music.MusicAppDiscovery
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.idriveconnectionkit.rhmi.RHMIAction
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import me.hufman.idriveconnectionkit.rhmi.RHMIModel
import me.hufman.idriveconnectionkit.rhmi.RHMIState

class AppSwitcherView(val state: RHMIState, val appDiscovery: MusicAppDiscovery, val controller: MusicController, val phoneAppResources: PhoneAppResources) {

	private val TAG = "MusicAppSwitcherView"
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty()
		}
	}

	val listApps: RHMIComponent.List
	val apps = ArrayList<MusicAppInfo>()
	val appsEmptyList = RHMIModel.RaListModel.RHMIListConcrete(3)
	val appsListAdapter = object: RHMIListAdapter<MusicAppInfo>(3, apps) {
		override fun convertRow(index: Int, item: MusicAppInfo): Array<Any> {
			val appIcon = phoneAppResources.getBitmap(item.icon, 48, 48)
			val checkbox = BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, 149)
			return arrayOf(
				if (item == controller.currentApp?.musicAppInfo) checkbox else "",
				BMWRemoting.RHMIResourceData(BMWRemoting.RHMIResourceType.IMAGEDATA, appIcon),
				item.name
			)
		}
	}

	init {
		listApps = state.componentsList.filterIsInstance<RHMIComponent.List>().first()
		appsEmptyList.addRow(arrayOf("", "", "<No Apps>"))
	}

	fun initWidgets(playbackView: PlaybackView) {
		state.getTextModel()?.asRaDataModel()?.value = "Apps"
		listApps.setVisible(true)
		listApps.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = playbackView.state.id
		listApps.getAction()?.asRAAction()?.rhmiActionCallback = object: RHMIAction.RHMIActionCallback {
			override fun onActionEvent(args: Map<*, *>?) {
				val index = Utils.etchAsInt(args?.get(1.toByte()))
				onClick(index)
			}
		}
	}

	fun show() {
		apps.clear()
		apps.addAll(appDiscovery.validApps)
		if (apps.size == 0) {
			listApps.setSelectable(false)
			listApps.getModel()?.setValue(appsEmptyList, 0, appsEmptyList.height, appsEmptyList.height)
		} else {
			listApps.setSelectable(true)
			listApps.getModel()?.setValue(appsListAdapter, 0, appsListAdapter.height, appsListAdapter.height)
		}
	}

	private fun onClick(index: Int) {
		val app = apps.getOrNull(index) ?: return
		Log.i(TAG, "User selected app ${app.name}")
		controller.connectApp(app)
	}
}