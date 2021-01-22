package me.hufman.androidautoidrive.carapp.music.views

import android.util.Log
import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.carapp.music.AVContextHandler
import me.hufman.androidautoidrive.carapp.music.MusicImageIDs
import me.hufman.androidautoidrive.music.MusicAppDiscovery
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.idriveconnectionkit.rhmi.*

class AppSwitcherView(val state: RHMIState, val appDiscovery: MusicAppDiscovery, val avContext: AVContextHandler, val graphicsHelpers: GraphicsHelpers, val musicImageIDs: MusicImageIDs) {

	private val TAG = "MusicAppSwitcherView"
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty()
		}
	}

	val listApps: RHMIComponent.List = state.componentsList.filterIsInstance<RHMIComponent.List>().first()
	val appsEmptyList = RHMIModel.RaListModel.RHMIListConcrete(3).apply {
		this.addRow(arrayOf("", "", L.MUSIC_APPLIST_EMPTY))
	}
	var visible = false
	val apps = ArrayList<MusicAppInfo>()
	val appsListAdapter = object: RHMIListAdapter<MusicAppInfo>(3, apps) {
		override fun convertRow(index: Int, item: MusicAppInfo): Array<Any> {
			val appIcon = graphicsHelpers.compress(item.icon, 48, 48)
			val checkbox = BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, musicImageIDs.CHECKMARK)
			return arrayOf(
				if (item == avContext.controller.currentAppInfo) checkbox else "",
				BMWRemoting.RHMIResourceData(BMWRemoting.RHMIResourceType.IMAGEDATA, appIcon),
				item.name
			)
		}
	}


	fun initWidgets(playbackView: PlaybackView) {
		state.focusCallback = FocusCallback { focused ->
			visible = focused
			if (focused) {
				show()
				appDiscovery.discoverAppsAsync()
			}
		}
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
			val index = apps.indexOfFirst { it == avContext.controller.currentAppInfo }
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