package me.hufman.androidautoidrive.carapp.music.views

import android.util.Log
import io.bimmergestalt.idriveconnectkit.rhmi.FocusCallback
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIAction
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import kotlinx.coroutines.Deferred
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.music.MusicImageIDs
import me.hufman.androidautoidrive.music.MusicAction
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import java.util.*

data class BrowseState(val location: MusicMetadata?,    // the directory the user selected
                       var allLocations: MutableList<MusicMetadata?>,      // all locations, including any that were shortcutted
                       var pageModel: BrowsePageModel? = null,      // the data to display in the BrowsePageView
                       var pageView: BrowsePageView? = null,    // the PageView that is showing for this location
)

class BrowseView(val states: List<RHMIState>, val musicController: MusicController, val musicImageIDs: MusicImageIDs, val graphicsHelpers: GraphicsHelpers) {
	companion object {
		val SEARCHRESULT_PLAY_FROM_SEARCH = MusicMetadata(mediaId="__PLAY_FROM_SEARCH__", title= L.MUSIC_BROWSE_PLAY_FROM_SEARCH)
		fun fits(state: RHMIState): Boolean {
			return BrowsePageView.fits(state)
		}
	}

	val stack = LinkedList<BrowseState>()
	val pageStack: List<BrowsePageView>    // the pages of browsing to pop off
		get() = stack.map { it.pageView }.filterNotNull()
	val locationStack: List<MusicMetadata?>   // the directories we navigated to before
		get() = stack.map { it.location }
	lateinit var playbackView: PlaybackView
	lateinit var inputState: RHMIState
	lateinit var pageController: BrowsePageController
	var visible = false
	var lastApp: MusicAppInfo? = null
	var currentPage: BrowsePageView? = null


	fun initWidgets(playbackView: PlaybackView, inputState: RHMIState) {
		// initialize common properties of the pages
		states.forEach {
			BrowsePageView.initWidgets(it)
			it.focusCallback = FocusCallback { focused ->
				Log.d("BrowseView", "Received focusedCallback for ${it.id}: $focused")
				visible = focused
				if (focused) {
					show(it.id)
				} else {
					hide(it.id)
				}
			}
		}
		this.playbackView = playbackView
		this.inputState = inputState
		pageController = BrowsePageController(this, musicController, playbackView)
	}

	/**
	 * When we are entering the Browsing state from PlaybackView,
	 * clear out the pages so we start from the top of the directory
	 * Don't clear out the locationStack so that we can draw checkmarks later
	 */
	fun clearPages() {
		stack.forEach {
			it.pageView = null
		}
	}

	/**
	 * When a state is shown, check whether we are expecting it
	 */
	private fun show(stateId: Int) {
		// track the last app we played and reset if it changed
		if (lastApp != null && lastApp != musicController.currentAppInfo) {
			stack.clear()
		}
		lastApp = musicController.currentAppInfo

		// handle back presses
		if (pageStack.isNotEmpty() && stateId != pageStack.last().state.id) {
			// the system showed a page by the user pressing back, pop off the stack
			stack.last { it.pageView != null}.apply {
				pageView?.hide()
				pageView = null
			}
		}
		// show the top of the stack if we popped off everything
		if (pageStack.isEmpty()) {
			// we might have backed out of the top of the browse, perhaps by switching apps
			pushBrowsePage(null, stateId)
		}
		// show the content for the page that we are showing
		val currentPage = pageStack.last()
		this.currentPage = currentPage
		if (stateId == currentPage.state.id) {
			// the system showed the page that was just added, load the info for it
			currentPage.initWidgets(inputState)
			currentPage.show()
		}
	}

	/**
	 * Returns the next [RHMIState] to be used.
	 */
	private fun getNextState(): RHMIState {
		val topPage = pageStack.lastOrNull()
		val topPageIndex = states.indexOfFirst { it.id == topPage?.state?.id }
		val nextStateIndex = (topPageIndex + 1) % states.size

		// don't want the next state to be the original page state as the back action on original page state will exit to PlaybackView
		return if (pageStack.size > 1 && nextStateIndex == 0) {
			states[nextStateIndex+1]
		} else {
			states[nextStateIndex]
		}
	}

	fun pushBrowsePage(directory: MusicMetadata?, stateId: Int? = null): BrowsePageView {
		val nextState = getNextState()
		val state = states.firstOrNull { it.id == stateId } ?: nextState
		val index = stack.indexOfLast { it.pageView != null } + 1 // what the next new index will be

		val stackSlot = if (directory != null && directory == stack.lastOrNull { it.location?.browseable == true }?.location) {
			// if we are doing a Jump Back to the last directory, don't clear out the stack
			// just open up the target directory
			stack.last {it.location?.browseable == true}
		} else if (index < stack.size && stack.getOrNull(index)?.location == directory) {
			// if we are navigating through the list along the same path, use the same slot
			stack[index]
		} else {
			// if we are in a different browse path, clear the remainder of the path
			// and then add this current selection
			stack.subList(index, stack.size).clear()
			BrowseState(directory, mutableListOf(directory)).also { stack.add(it) }
		}

		// update the top page's model to declare it to be jumpable
		// the top page isn't technically browseable, because null, so check for any deeper browseable
		stack[0].pageModel?.showJumpbackAction = locationStack.any { it?.browseable == true }

		// then push the next page's model
		val title = BrowsePageModel.getTitle(musicController.currentAppInfo, stackSlot.allLocations)
		val contents = musicController.browseAsync(stackSlot.allLocations.last())       // use any previously-shortcutted location
		val previouslySelected = stack.getOrNull(index+1)?.location
		val jumpable = false        // pushed pages are never browsable, we update the top page later
		val searchable = directory == null && ((musicController.currentAppInfo?.searchable ?: false) || musicController.isSupportedAction(MusicAction.PLAY_FROM_SEARCH))
		val browseModel = BrowsePageModel(title, contents, previouslySelected, jumpable, searchable, true, false)
		val browsePage = BrowsePageView(state, musicImageIDs, browseModel, pageController, graphicsHelpers)
		browsePage.initWidgets(inputState)
		stackSlot.pageModel = browseModel
		stackSlot.pageView = browsePage
		return browsePage
	}

	fun pushSearchResultPage(deferredSearchResults: Deferred<List<MusicMetadata>?>): BrowsePageView {
		val state = getNextState()
		val index = stack.indexOfLast { it.pageView != null } + 1 // what the next new index will be

		stack.subList(index, stack.size).clear()
		val stackSlot = BrowseState(null, mutableListOf()).apply { stack.add(this) }
		val browseModel = BrowsePageModel(L.MUSIC_SEARCH_RESULTS_LABEL, deferredSearchResults, null, false, false, false, true)
		val browsePage = BrowsePageView(state, musicImageIDs, browseModel, pageController, graphicsHelpers)
		browsePage.initWidgets(inputState)
		stackSlot.pageModel = browseModel
		stackSlot.pageView = browsePage
		return browsePage
	}

	fun shortcutBrowsePage(directory: MusicMetadata?) {
		val browseState = stack.lastOrNull { it.location?.browseable != false } ?: return
		browseState.allLocations.add(directory)
		browseState.pageModel?.title = BrowsePageModel.getTitle(musicController.currentAppInfo, browseState.allLocations)
		browseState.pageModel?.contents = musicController.browseAsync(directory)
		browseState.pageView?.show()
	}

	fun openFilterInput(browsePageModel: BrowsePageModel): FilterInputView {
		val nextState = FilterInputView(inputState, pageController, browsePageModel)
		nextState.show()

		return nextState
	}

	fun openSearchInput(): SearchInputView {
		val nextState = SearchInputView(inputState, musicController, pageController)
		nextState.show()

		return nextState
	}

	fun playSong(song: MusicMetadata) {
		// clear out any previous navigation past this song
		val index = stack.indexOfLast { it.pageView != null } + 1
		stack.subList(index, stack.size).clear()

		// remember the song as the last selected item
		stack.last.pageModel?.previouslySelected = song
		stack.add(BrowseState(song, mutableListOf(song)))

		// now actually play
		musicController.playSong(song)
	}

	fun redraw() {
		currentPage?.redraw()
	}

	/**
	 * When a state gets hidden, inform it, so it can disconnect any listeners
	 */
	private fun hide(stateId: Int) {
		pageStack.lastOrNull { it.state.id == stateId }?.hide()
	}
}

data class BrowsePageModel(var title: String, var contents: Deferred<List<MusicMetadata>?>,
                           var previouslySelected: MusicMetadata?,
                           var showJumpbackAction: Boolean, var showSearchAction: Boolean, var showFilterAction: Boolean,
                           var isSearchResultView: Boolean) {
	companion object {
		fun getTitle(appInfo: MusicAppInfo?, locations: List<MusicMetadata?>): String {
			return when (locations.size) {
				0 -> appInfo?.name ?: ""
				1 -> if (locations.first()?.subtitle == "Album" || locations.first()?.subtitle == "Show") {
					"${locations.first()?.title} - ${locations.first()?.artist}"
				} else {
					locations.first()?.title ?: appInfo?.name ?: ""
				}
				2 -> "${locations.first()?.title ?: ""} / ${locations.last()?.title ?: ""}"
				else -> "${locations.first()?.title ?: ""} /.. / ${locations.last()?.title ?: ""}"
			}
		}
	}
}

class BrowsePageController(private val browseView: BrowseView, private val musicController: MusicController, private val playbackView: PlaybackView) {
	/** Change the latest browse page to this directory, to shortcut through single-folder contents */
	fun shortcutBrowsePage(directory: MusicMetadata?) {
		browseView.shortcutBrowsePage(directory)
	}

	/** Push a new browse page with the deepest directory that was previously browse */
	fun jumpBack(hmiAction: RHMIAction.HMIAction?) {
		val nextPage = browseView.pushBrowsePage(browseView.locationStack.lastOrNull {it?.browseable == true})
		hmiAction?.getTargetModel()?.asRaIntModel()?.value = nextPage.state.id
	}

	/** Open the Filter Input screen */
	fun openFilterInput(hmiAction: RHMIAction.HMIAction?, browsePageModel: BrowsePageModel) {
		val nextState = browseView.openFilterInput(browsePageModel)
		hmiAction?.getTargetModel()?.asRaIntModel()?.value = nextState.state.id
	}

	/** Open the Search Input screen */
	fun openSearchInput(hmiAction: RHMIAction.HMIAction?) {
		val nextState = browseView.openSearchInput()
		hmiAction?.getTargetModel()?.asRaIntModel()?.value = nextState.state.id
	}

	/** Call the musicController's playFromSearch command */
	fun playFromSearch(hmiAction: RHMIAction.HMIAction?, search: String) {
		musicController.playFromSearch(search)
		hmiAction?.getTargetModel()?.asRaIntModel()?.value = playbackView.state.id
	}

	/** Push a browse page or play a song, and then update the given HMIAction's target state */
	fun onListSelection(hmiAction: RHMIAction.HMIAction?, entry: MusicMetadata) {
		if (entry.browseable) {
			val nextPage = browseView.pushBrowsePage(entry)
			hmiAction?.getTargetModel()?.asRaIntModel()?.value = nextPage.state.id
		}
		else {
			if (entry.playable) {
				browseView.playSong(entry)
			}
			hmiAction?.getTargetModel()?.asRaIntModel()?.value = playbackView.state.id
		}
	}

	/** Show a search result page and then update the given HMIAction's target state */
	fun showSearchResults(deferredSearchResults: Deferred<List<MusicMetadata>?>, hmiAction: RHMIAction.HMIAction?) {
		val nextPage = browseView.pushSearchResultPage(deferredSearchResults)
		hmiAction?.getTargetModel()?.asRaIntModel()?.value = nextPage.state.id
	}
}