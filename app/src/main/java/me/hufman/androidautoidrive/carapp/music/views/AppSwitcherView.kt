package me.hufman.androidautoidrive.carapp.music.views

import android.util.Log
import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.carapp.music.AVContextHandler
import me.hufman.androidautoidrive.music.MusicAppDiscovery
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.idriveconnectionkit.rhmi.*

class AppSwitcherView(val state: RHMIState, val appDiscovery: MusicAppDiscovery, val avContext: AVContextHandler, val phoneAppResources: PhoneAppResources) {

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
				if (item == avContext.controller.musicBrowser?.musicAppInfo) checkbox else "",
				BMWRemoting.RHMIResourceData(BMWRemoting.RHMIResourceType.IMAGEDATA, appIcon),
				item.name
			)
		}
	}

	init {
		listApps = state.componentsList.filterIsInstance<RHMIComponent.List>().first()
		appsEmptyList.addRow(arrayOf("", "", L.MUSIC_APPLIST_EMPTY))
	}

	fun initWidgets(playbackView: PlaybackView) {
		state.getTextModel()?.asRaDataModel()?.value = L.MUSIC_APPLIST_TITLE
		listApps.setVisible(true)
		listApps.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = playbackView.state.id
		listApps.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { onClick(it) }
	}

	/**
	 * When the AppSwitcherView is first displayed
	 * draw the current list of apps, and set the cursor to the connected app
	 */
	fun show() {
		redraw()

		if (apps.isNotEmpty()) {
			val index = apps.indexOfFirst { it == avContext.controller.musicBrowser?.musicAppInfo }
			if (index >= 0) {
				state.app.events.values.firstOrNull { it is RHMIEvent.FocusEvent }?.triggerEvent(
						mapOf(0.toByte() to listApps.id, 41.toByte() to index)
				)
			}
		}
	}

	fun redraw() {
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
		avContext.av_requestContext(app)
	}
}