package me.hufman.androidautoidrive.carapp.music.views

import android.util.Log
import de.bmw.idrive.BMWRemoting
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.Utils
import me.hufman.androidautoidrive.awaitPending
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.idriveconnectionkit.rhmi.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.min

class BrowsePageView(val state: RHMIState, val browseView: BrowseView, val folder: MusicMetadata?, var previouslySelected: MusicMetadata?): CoroutineScope {
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.IO

	companion object {
		const val LOADING_TIMEOUT = 300
		val emptyList = RHMIModel.RaListModel.RHMIListConcrete(3).apply {
			this.addRow(arrayOf("", "", "<Empty>"))
		}
		val loadingList = RHMIModel.RaListModel.RHMIListConcrete(3).apply {
			this.addRow(arrayOf("", "", "<Loading>"))
		}
		val checkmarkIcon = BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, 149)
		val folderIcon = BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, 155)
		val songIcon = BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, 152)

		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty() &&
					state.componentsList.filterIsInstance<RHMIComponent.Image>().isEmpty()
		}

		fun initWidgets(browsePageState: RHMIState, playbackView: PlaybackView) {
			// do any common initialization here
			val musicListComponent = browsePageState.componentsList.filterIsInstance<RHMIComponent.List>().first()
			musicListComponent.setVisible(true)
			musicListComponent.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH, "57,50,*")
		}
	}

	private var loaderJob: Job? = null
	private var musicListComponent: RHMIComponent.List

	private var musicList = ArrayList<MusicMetadata>()
	private var currentListModel: RHMIModel.RaListModel.RHMIList = loadingList

	init {
		musicListComponent = state.componentsList.filterIsInstance<RHMIComponent.List>().first()
	}

	fun initWidgets(playbackView: PlaybackView) {
		// handle clicks
		musicListComponent.getAction()?.asRAAction()?.rhmiActionCallback = object: RHMIAction.RHMIActionCallback {
			override fun onActionEvent(args: Map<*, *>?) {
				val index = Utils.etchAsInt(args?.get(1.toByte()))
				val entry = musicList.getOrNull(index) ?: return
				println("User selected browse entry $entry")
				previouslySelected = entry  // update the selection state for future redraws
				if (entry.browseable) {
					val nextPage = browseView.pushBrowsePage(entry)
					musicListComponent.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = nextPage.state.id
				}
				else {
					if (entry.playable) {
						browseView.playSong(entry)
					}
					musicListComponent.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = playbackView.state.id
				}
			}
		}
	}

	fun show() {
		// update the list whenever the car requests some more data
		musicListComponent.requestDataCallback = RequestDataCallback { startIndex, numRows ->
			Log.i("BrowsePageView", "Car requested more data, $startIndex:${startIndex+numRows}")
			showList(startIndex, numRows)
		}

		// start loading data
		loaderJob = launch(Dispatchers.IO) {
			val musicListDeferred = browseView.musicController.browseAsync(folder)
			val musicList = musicListDeferred.awaitPending(LOADING_TIMEOUT) {
				currentListModel = loadingList
				showList()
			}
			this@BrowsePageView.musicList.clear()
			this@BrowsePageView.musicList.addAll(musicList)

			if (musicList.isEmpty()) {
				currentListModel = emptyList
				showList()
			} else {
				currentListModel = object: RHMIListAdapter<MusicMetadata>(3, musicList) {
					override fun convertRow(index: Int, item: MusicMetadata): Array<Any> {
						return arrayOf(
								if (previouslySelected == item) checkmarkIcon else "",
								if (item.browseable) folderIcon else
									if (item.playable) songIcon else "",
								item.title ?: ""
						)
					}
				}
				showList()
				val previouslySelected = this@BrowsePageView.previouslySelected
				if (previouslySelected != null) {
					val index = musicList.indexOf(previouslySelected)
					if (index >= 0) {
						state.app.events.values.firstOrNull { it is RHMIEvent.FocusEvent }?.triggerEvent(
								mapOf(0.toByte() to musicListComponent.id, 41.toByte() to index)
						)
					}
				} else {
					state.app.events.values.firstOrNull { it is RHMIEvent.FocusEvent }?.triggerEvent(
							mapOf(0.toByte() to musicListComponent.id, 41.toByte() to 0)
					)
				}
			}
		}
	}

	private fun showList(startIndex: Int = 0, numRows: Int = 20) {
		if (currentListModel === emptyList || currentListModel === loadingList) {
			musicListComponent.setEnabled(false)
		} else {
			musicListComponent.setEnabled(true)
		}
		if (numRows < currentListModel.height) {
			musicListComponent.setProperty(RHMIProperty.PropertyId.VALID, false)
		} else {
			musicListComponent.setProperty(RHMIProperty.PropertyId.VALID, true)
		}
		musicListComponent.getModel()?.setValue(currentListModel, startIndex, numRows, currentListModel.height)
	}

	fun hide() {
		// cancel any loading
		loaderJob?.cancel()
		musicListComponent.requestDataCallback = null
	}
}