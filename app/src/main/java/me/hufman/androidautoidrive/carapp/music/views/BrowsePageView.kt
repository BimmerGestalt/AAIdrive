package me.hufman.androidautoidrive.carapp.music.views

import android.util.Log
import de.bmw.idrive.BMWRemoting
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.awaitPending
import me.hufman.androidautoidrive.carapp.InputState
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.carapp.music.MusicImageIDs
import me.hufman.androidautoidrive.music.MusicAction
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.idriveconnectionkit.rhmi.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.coroutines.CoroutineContext

enum class BrowseAction(val getLabel: () -> String) {
	JUMPBACK({ L.MUSIC_BROWSE_ACTION_JUMPBACK }),
	FILTER({ L.MUSIC_BROWSE_ACTION_FILTER }),
	SEARCH({ L.MUSIC_BROWSE_ACTION_SEARCH });

	override fun toString(): String {
		return getLabel()
	}
}
class BrowsePageView(val state: RHMIState, val musicImageIDs: MusicImageIDs, val browsePageModel: BrowsePageModel, val browseController: BrowsePageController, var previouslySelected: MusicMetadata?): CoroutineScope {
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.IO

	companion object {
		const val LOADING_TIMEOUT = 2000
		const val TAG = "BrowsePageView"

		val emptyList = RHMIModel.RaListModel.RHMIListConcrete(3).apply {
			this.addRow(arrayOf("", "", L.MUSIC_BROWSE_EMPTY))
		}
		val loadingList = RHMIModel.RaListModel.RHMIListConcrete(3).apply {
			this.addRow(arrayOf("", "", L.MUSIC_BROWSE_LOADING))
		}

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
	private var folderNameLabel: RHMIComponent.Label
	private var actionsListComponent: RHMIComponent.List
	private var musicListComponent: RHMIComponent.List

	private var folder = browsePageModel.folder
	private var musicList = ArrayList<MusicMetadata>()
	private var currentListModel: RHMIModel.RaListModel.RHMIList = loadingList
	private var shortcutSteps = 0

	val checkmarkIcon = BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, musicImageIDs.CHECKMARK)
	val folderIcon = BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, musicImageIDs.BROWSE)
	val songIcon = BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, musicImageIDs.SONG)

	private val actions = ArrayList<BrowseAction>()
	private val actionsListModel = RHMIListAdapter<BrowseAction>(3, actions)

	init {
		folderNameLabel = state.componentsList.filterIsInstance<RHMIComponent.Label>().first()
		actionsListComponent = state.componentsList.filterIsInstance<RHMIComponent.List>()[0]
		musicListComponent = state.componentsList.filterIsInstance<RHMIComponent.List>()[1]
	}

	fun initWidgets(inputState: RHMIState) {
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
					showFilterInput(inputState)
				}
				BrowseAction.SEARCH -> {
					musicListComponent.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = inputState.id
					showSearchInput(inputState)
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
			if (this@BrowsePageView.musicList.isEmpty()) {
				musicListComponent.setEnabled(false)
				currentListModel = loadingList
				showList()
			}
			val musicListDeferred = browsePageModel.browseAsync(folder)
			val musicList = musicListDeferred.awaitPending(LOADING_TIMEOUT) {
				Log.d(TAG, "Browsing ${folder?.mediaId} timed out, retrying")
				show()
				delay(100)
				return@launch
			}
			this@BrowsePageView.musicList.clear()
			this@BrowsePageView.musicList.addAll(musicList)
			Log.d(TAG, "Browsing ${folder?.mediaId} resulted in ${musicList.count()} items")

			if (musicList.isEmpty()) {
				musicListComponent.setEnabled(false)
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
								UnicodeCleaner.clean(item.title ?: "")
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
			if (browsePageModel.folder == null && (
					browsePageModel.musicAppInfo?.searchable == true ||
					browsePageModel.isSupportedAction(MusicAction.PLAY_FROM_SEARCH))) {
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

	fun showFilterInput(inputState: RHMIState) {
		val showState = object: InputState<MusicMetadata>(inputState) {
			override fun onEntry(input: String) {
				val suggestions = musicList.asSequence().filter {
					UnicodeCleaner.clean(it.title ?: "").split(Regex("\\s+")).any { word ->
						word.toLowerCase().startsWith(input.toLowerCase())
					}
				} + musicList.asSequence().filter {
					UnicodeCleaner.clean(it.title?: "").toLowerCase().contains(input.toLowerCase())
				}
				sendSuggestions(suggestions.take(15).distinct().toList())
			}

			override fun onSelect(item: MusicMetadata, index: Int) {
				previouslySelected = item  // update the selection state for future redraws
				browseController.onListSelection(item, inputComponent.getSuggestAction()?.asHMIAction())
			}

			override fun convertRow(row: MusicMetadata): String {
				return UnicodeCleaner.clean(row.title ?: "")
			}
		}
	}

	fun showSearchInput(inputState: RHMIState) {
		val showState = object : InputState<MusicMetadata>(inputState) {
			val SEARCHRESULT_SEARCHING = MusicMetadata(mediaId="__SEARCHING__", title=L.MUSIC_BROWSE_SEARCHING)
			val SEARCHRESULT_EMPTY = MusicMetadata(mediaId="__EMPTY__", title=L.MUSIC_BROWSE_EMPTY)
			val MAX_RETRIES = 2
			var searchRetries = MAX_RETRIES

			override fun onEntry(input: String) {
				searchRetries = MAX_RETRIES
				search(input)
			}

			fun search(input: String) {
				if (input.length >= 2 && searchRetries > 0) {
					searchJob?.cancel()
					searchJob = launch(Dispatchers.IO) {
						sendSuggestions(listOf(SEARCHRESULT_SEARCHING))
						val suggestionsDeferred = browsePageModel.searchAsync(input)
						val suggestions = suggestionsDeferred.awaitPending(LOADING_TIMEOUT) {
							Log.d(TAG, "Searching ${browsePageModel.musicAppInfo?.name} for \"$input\" timed out, retrying")
							searchRetries -= 1
							search(input)
							return@launch
						}
						sendSuggestions(suggestions ?: LinkedList())
					}
				} else if (input.length >= 2) {
					// too many retries
					sendSuggestions(listOf(SEARCHRESULT_EMPTY))
				}
			}

			override fun sendSuggestions(newSuggestions: List<MusicMetadata>) {
				val fullSuggestions = if (browsePageModel.isSupportedAction(MusicAction.PLAY_FROM_SEARCH)) {
					listOf(BrowseView.SEARCHRESULT_PLAY_FROM_SEARCH) + newSuggestions
				} else {
					newSuggestions
				}
				super.sendSuggestions(fullSuggestions)
			}

			override fun onSelect(item: MusicMetadata, index: Int) {
				if (item == SEARCHRESULT_EMPTY || item == SEARCHRESULT_SEARCHING) {
					// invalid selection, don't change states
					inputComponent.getSuggestAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = 0
					throw RHMIActionAbort()
				} else if (item == BrowseView.SEARCHRESULT_PLAY_FROM_SEARCH) {
					browseController.playFromSearch(this.input)
					browseController.onListSelection(item, inputComponent.getSuggestAction()?.asHMIAction())
				} else {
					previouslySelected = item  // update the selection state for future redraws
					browseController.onListSelection(item, inputComponent.getSuggestAction()?.asHMIAction())
				}
			}

			override fun convertRow(row: MusicMetadata): String {
				if (row.subtitle != null) {
					return UnicodeCleaner.clean("${row.title}\n${row.subtitle}")
				} else {
					return UnicodeCleaner.clean(row.title ?: "")
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