package me.hufman.androidautoidrive.carapp.music.views

import android.util.Log
import de.bmw.idrive.BMWRemoting
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.awaitPending
import me.hufman.androidautoidrive.carapp.InputState
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.idriveconnectionkit.rhmi.*
import kotlin.coroutines.CoroutineContext

enum class BrowseAction(val getLabel: () -> String) {
	JUMPBACK({ L.MUSIC_BROWSE_ACTION_JUMPBACK }),
	FILTER({ L.MUSIC_BROWSE_ACTION_FILTER }),
	SEARCH({ L.MUSIC_BROWSE_ACTION_SEARCH });

	override fun toString(): String {
		return getLabel()
	}
}
class BrowsePageView(val state: RHMIState, val browsePageModel: BrowsePageModel, val browseController: BrowsePageController, var previouslySelected: MusicMetadata?): CoroutineScope {
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.IO

	companion object {
		const val LOADING_TIMEOUT = 3000
		const val TAG = "BrowsePageView"

		val emptyList = RHMIModel.RaListModel.RHMIListConcrete(3).apply {
			this.addRow(arrayOf("", "", L.MUSIC_BROWSE_EMPTY))
		}
		val loadingList = RHMIModel.RaListModel.RHMIListConcrete(3).apply {
			this.addRow(arrayOf("", "", L.MUSIC_BROWSE_LOADING))
		}
		val searchingList = RHMIModel.RaListModel.RHMIListConcrete(3).apply {
			this.addRow(arrayOf(L.MUSIC_BROWSE_SEARCHING))
		}
		val checkmarkIcon = BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, 149)
		val folderIcon = BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, 155)
		val songIcon = BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, 152)

		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
					state.componentsList.filterIsInstance<RHMIComponent.Label>().isNotEmpty() &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().size >= 2 &&
					state.componentsList.filterIsInstance<RHMIComponent.Image>().isEmpty()
		}

		fun initWidgets(browsePageState: RHMIState) {
			// do any common initialization here
			val actionsListComponent = browsePageState.componentsList.filterIsInstance<RHMIComponent.List>()[0]
			actionsListComponent.setVisible(true)
			actionsListComponent.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH, "0,0,*")
			val musicListComponent = browsePageState.componentsList.filterIsInstance<RHMIComponent.List>()[1]
			musicListComponent.setVisible(true)
			musicListComponent.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH, "57,50,*")
			// set up dynamic paging
			musicListComponent.setProperty(RHMIProperty.PropertyId.VALID, false)
			// set the page title
			browsePageState.getTextModel()?.asRaDataModel()?.value = L.MUSIC_BROWSE_TITLE
		}
	}

	private var loaderJob: Job? = null
	private var searchJob: Job? = null
	private lateinit var playbackView: PlaybackView
	private var folderNameLabel: RHMIComponent.Label
	private var actionsListComponent: RHMIComponent.List
	private var musicListComponent: RHMIComponent.List

	private var folder = browsePageModel.folder
	private var musicList = ArrayList<MusicMetadata>()
	private var currentListModel: RHMIModel.RaListModel.RHMIList = loadingList
	private var shortcutSteps = 0

	private val actions = ArrayList<BrowseAction>()
	private val actionsListModel = RHMIListAdapter<BrowseAction>(3, actions)

	init {
		folderNameLabel = state.componentsList.filterIsInstance<RHMIComponent.Label>().first()
		actionsListComponent = state.componentsList.filterIsInstance<RHMIComponent.List>()[0]
		musicListComponent = state.componentsList.filterIsInstance<RHMIComponent.List>()[1]
	}

	fun initWidgets(inputState: RHMIState) {
		val inputComponent = inputState.componentsList.filterIsInstance<RHMIComponent.Input>().first()
		folderNameLabel.setVisible(true)
		// handle action clicks
		actionsListComponent.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index ->
			val action = actions.getOrNull(index)
			when (action) {
				BrowseAction.JUMPBACK -> {
					browseController.jumpBack(musicListComponent.getAction()?.asHMIAction())
				}
				BrowseAction.FILTER -> {
					musicListComponent.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = inputState.id
					showFilterInput(inputComponent)
				}
				BrowseAction.SEARCH -> {
					musicListComponent.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = inputState.id
					showSearchInput(inputComponent)
				}
			}
		}
		// handle song clicks
		musicListComponent.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index ->
			val entry = musicList.getOrNull(index)
			if (entry != null) {
				Log.i(TAG,"User selected browse entry $entry")

				previouslySelected = entry  // update the selection state for future redraws
				browseController.onListSelection(entry, musicListComponent.getAction()?.asHMIAction())
			} else {
				Log.w(TAG, "User selected index $index but the list is only ${musicList.size} long")
			}
		}
	}

	fun show() {
		// show the name of the directory
		folderNameLabel.getModel()?.asRaDataModel()?.value = when(shortcutSteps) {
			0 -> folder?.title ?: browsePageModel.musicAppInfo?.name ?: ""
			1 -> "${browsePageModel.folder?.title ?: ""} / ${folder?.title ?: ""}"
			else -> "${browsePageModel.folder?.title ?: ""} /../ ${folder?.title ?: ""}"
		}

		showActionsList()

		// update the list whenever the car requests some more data
		musicListComponent.requestDataCallback = RequestDataCallback { startIndex, numRows ->
			Log.i(TAG, "Car requested more data, $startIndex:${startIndex+numRows}")
			showList(startIndex, numRows)
		}

		// start loading data
		loaderJob?.cancel()
		loaderJob = launch(Dispatchers.IO) {
			musicListComponent.setEnabled(false)
			currentListModel = loadingList
			showList()
			val musicListDeferred = browsePageModel.browseAsync(folder)
			val musicList = musicListDeferred.awaitPending(LOADING_TIMEOUT) {
				Log.d(TAG, "Browsing ${folder?.mediaId} timed out, retrying")
				show()
				return@launch
			}
			this@BrowsePageView.musicList.clear()
			this@BrowsePageView.musicList.addAll(musicList)
			Log.d(TAG, "Browsing ${folder?.mediaId} resulted in ${musicList.count()} items")

			if (musicList.isEmpty()) {
				currentListModel = emptyList
				showList()
			} else if (isSingleFolder(musicList)) {
				// keep the loadingList in place
				// navigate to the next deeper directory
				folder = musicList.first { it.browseable }
				shortcutSteps += 1
				show()  // show the next page deeper
				return@launch
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
				musicListComponent.setEnabled(true)
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

				// having the song list loaded may change the available actions
				showActionsList()
			}
		}
	}

	/**
	 * An single directory is defined as one with a single subdirectory inside it
	 * Playable entries before this single folder are ignored, because they are usually "Play All" entries or something
	 */
	private fun isSingleFolder(musicList: List<MusicMetadata>): Boolean {
		return musicList.indexOfFirst { it.browseable } == musicList.size - 1
	}

	private fun showActionsList() {
		synchronized(actions) {
			actions.clear()
			if (browsePageModel.folder == null && browsePageModel.musicAppInfo?.searchable == true) {
				actions.add(BrowseAction.SEARCH)
			}
			if (browsePageModel.folder == null && browsePageModel.jumpbackFolder() != null) {
				// the top of locationStack is always a single null element for the root
				// we have previously browsed somewhere if locationStack.size > 1
				actions.add(BrowseAction.JUMPBACK)
			}
			if (musicList.isNotEmpty()) {
				actions.add(BrowseAction.FILTER)
			}
			actionsListComponent.getModel()?.setValue(actionsListModel, 0, actionsListModel.height, actionsListModel.height)
		}
	}

	private fun showList(startIndex: Int = 0, numRows: Int = 20) {
		musicListComponent.getModel()?.setValue(currentListModel, startIndex, numRows, currentListModel.height)
	}

	private fun showFilterInput(inputComponent: RHMIComponent.Input) {
		val inputState = object: InputState<MusicMetadata>(inputComponent) {
			override fun onEntry(input: String) {
				val suggestions = musicList.asSequence().filter {
					(it.title ?: "").split(Regex("\\s+")).any { word ->
						word.toLowerCase().startsWith(input.toLowerCase())
					}
				} + musicList.asSequence().filter {
					it.title?.toLowerCase()?.contains(input.toLowerCase()) ?: false
				}
				sendSuggestions(suggestions.take(15).distinct().toList())
			}

			override fun onSelect(item: MusicMetadata, index: Int) {
				previouslySelected = item  // update the selection state for future redraws
				browseController.onListSelection(item, inputComponent.getSuggestAction()?.asHMIAction())
			}
		}
	}

	private fun showSearchInput(inputComponent: RHMIComponent.Input) {
		val inputState = object : InputState<MusicMetadata>(inputComponent) {
			override fun onEntry(input: String) {
				val inputState = this
				if (input.length >= 2) {
					searchJob?.cancel()
					searchJob = launch(Dispatchers.IO) {
						inputComponent.getSuggestModel()?.asRaListModel()?.setValue(searchingList, 0, searchingList.height, searchingList.height)
						val suggestionsDeferred = browsePageModel.searchAsync(input)
						val suggestions = suggestionsDeferred.awaitPending(LOADING_TIMEOUT) {
							Log.d(TAG, "Searching ${browsePageModel.musicAppInfo?.name} for \"$input\" timed out, retrying")
							inputState.onEntry(input)
							return@launch
						}
						inputState.sendSuggestions(suggestions)
					}
				}
			}

			override fun onSelect(item: MusicMetadata, index: Int) {
				previouslySelected = item  // update the selection state for future redraws
				browseController.onListSelection(item, inputComponent.getSuggestAction()?.asHMIAction())
			}

			override fun convertRow(row: MusicMetadata): String {
				if (row.subtitle != null) {
					return "${row.title}\n${row.subtitle}"
				} else {
					return row.title ?: ""
				}
			}
		}
	}

	fun hide() {
		// cancel any loading
		loaderJob?.cancel()
		searchJob?.cancel()
		musicListComponent.requestDataCallback = null
	}
}