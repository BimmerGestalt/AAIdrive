package me.hufman.androidautoidrive.carapp.music.views

import android.util.Log
import kotlinx.coroutines.Deferred
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import me.hufman.androidautoidrive.carapp.music.MusicApp
import me.hufman.androidautoidrive.carapp.music.MusicImageIDs
import me.hufman.androidautoidrive.music.MusicAction
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.idriveconnectionkit.rhmi.FocusCallback
import me.hufman.idriveconnectionkit.rhmi.RHMIAction
import me.hufman.idriveconnectionkit.rhmi.RHMIState
import java.util.*

data class BrowseState(val location: MusicMetadata?,    // the directory the user selected
                       var pageView: BrowsePageView? = null     // the PageView that is showing for this location
)
class BrowseView(val states: List<RHMIState>, val musicController: MusicController, val musicImageIDs: MusicImageIDs, val graphicsHelpers: GraphicsHelpers, val musicApp: MusicApp) {
	companion object {
		val SEARCHRESULT_PLAY_FROM_SEARCH = MusicMetadata(mediaId="__PLAY_FROM_SEARCH__", title=L.MUSIC_BROWSE_PLAY_FROM_SEARCH)
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
				this.pageView?.hide()
				this.pageView = null
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

	fun pushBrowsePage(directory: MusicMetadata?, stateId: Int? = null): BrowsePageView {
		val topPage = pageStack.lastOrNull()
		val topPageIndex = states.indexOfFirst { it.id == topPage?.state?.id }
		val nextStateIndex = (topPageIndex + 1) % states.size

		// don't want the next state to be the original page state as the back action on original page state will exit to PlaybackView
		val nextState = if (pageStack.size > 1 && nextStateIndex == 0) {
			states[nextStateIndex+1]
		} else {
			states[nextStateIndex]
		}

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
			BrowseState(directory).apply { stack.add(this) }
		}

		val browseModel = BrowsePageModel(this, musicController, directory)
		val browsePage = BrowsePageView(state, musicImageIDs, browseModel, pageController, stack.getOrNull(index+1)?.location, graphicsHelpers)
		browsePage.initWidgets(inputState)
		stackSlot.pageView = browsePage
		return browsePage
	}

	fun playSong(song: MusicMetadata) {
		// clear out any previous navigation past this song
		val index = stack.indexOfLast { it.pageView != null } + 1
		stack.subList(index, stack.size).clear()

		// remember the song as the last selected item
		pageStack.last().previouslySelected = song
		stack.add(BrowseState(song))

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

class BrowsePageModel(private val browseView: BrowseView, private val musicController: MusicController, val folder: MusicMetadata?) {
	val musicAppInfo: MusicAppInfo?
		get() = musicController.currentAppInfo

	fun isSupportedAction(action: MusicAction): Boolean {
		return musicController.isSupportedAction(action)
	}
	fun jumpbackFolder(): MusicMetadata? {
		return browseView.locationStack.lastOrNull { it?.browseable == true }
	}
	fun browseAsync(musicMetadata: MusicMetadata?): Deferred<List<MusicMetadata>> {
		return musicController.browseAsync(musicMetadata)
	}
	fun searchAsync(query: String): Deferred<List<MusicMetadata>?> {
		return musicController.searchAsync(query)
	}
}

class BrowsePageController(private val browseView: BrowseView, private val musicController: MusicController, private val playbackView: PlaybackView) {
	fun jumpBack(hmiAction: RHMIAction.HMIAction?) {
		val nextPage = browseView.pushBrowsePage(browseView.locationStack.lastOrNull {it?.browseable == true})
		hmiAction?.getTargetModel()?.asRaIntModel()?.value = nextPage.state.id
	}

	fun playFromSearch(search: String) {
		musicController.playFromSearch(search)
	}

	fun onListSelection(entry: MusicMetadata, hmiAction: RHMIAction.HMIAction?) {
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
}