package me.hufman.androidautoidrive.carapp.music.views

import android.util.Log
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.idriveconnectionkit.rhmi.FocusCallback
import me.hufman.idriveconnectionkit.rhmi.RHMIState
import me.hufman.idriveconnectionkit.rhmi.VisibleCallback
import java.util.*

class BrowseView(val states: List<RHMIState>, val musicController: MusicController) {
	companion object {
		fun fits(state: RHMIState): Boolean {
			return BrowsePageView.fits(state)
		}
	}

	val pageStack = LinkedList<BrowsePageView>()    // the pages of browsing to pop off
	val locationStack = ArrayList<MusicMetadata?>()   // the directories we navigated to before
	var playbackView: PlaybackView? = null

	init {
	}

	fun initWidgets(playbackView: PlaybackView) {
		// initialize common properties of the pages
		states.forEach {
			BrowsePageView.initWidgets(it, playbackView)
			it.focusCallback = FocusCallback { focused ->
				Log.d("BrowseView", "Received focusedCallback for ${it.id}: $focused")
				if (focused) {
					show(it.id)
				} else {
					hide(it.id)
				}
			}
			it.visibleCallback = VisibleCallback { visible ->
				Log.d("BrowseView", "Received visibleCallback for ${it.id}: $visible")
//				if (visible) {
//					show(it.id)
//				} else {
//					hide(it.id)
//				}
			}
		}
		this.playbackView = playbackView
	}

	/**
	 * When we are entering the Browsing state from PlaybackView,
	 * clear out the pages so we start from the top of the directory
	 * Don't clear out the locationStack so that we can draw checkmarks later
	 */
	fun clearPages() {
		pageStack.clear()
	}

	/**
	 * When a state is shown, check whether we are expecting it
	 */
	private fun show(stateId: Int) {
		if (pageStack.isEmpty()) {
			pushBrowsePage(null)
		}
		if (stateId == pageStack.last.state.id) {
			// the system showed the page that was just added, load the info for it
			pageStack.last.initWidgets(playbackView as PlaybackView)
			pageStack.last.show()
		} else {
			// the system showed a page by the user pressing back, pop off the stack
			pageStack.removeLast().hide()
			pageStack.lastOrNull()?.initWidgets(playbackView as PlaybackView)
			pageStack.lastOrNull()?.show()
		}
	}

	fun pushBrowsePage(directory: MusicMetadata?): BrowsePageView {
		val state = states[pageStack.size % states.size]
		val index = pageStack.size  // what the next index will be

		// if we are navigating through the list, clear out any previous alternate navigations
		// and then add this current selection
		if (locationStack.size == index || locationStack.getOrNull(index) != directory) {
			locationStack.subList(index, locationStack.size).clear()
			locationStack.add(directory)
		}
		val previouslySelected = locationStack.getOrNull(index+1)   // get the location for the next page, if present
		val browsePage = BrowsePageView(state, this, directory, previouslySelected)
		browsePage.initWidgets(playbackView as PlaybackView)
		pageStack.add(browsePage)
		val playbackView = this.playbackView
		if (playbackView != null) {
			browsePage.initWidgets(playbackView)
		}
		return browsePage
	}

	fun playSong(song: MusicMetadata) {
		val index = pageStack.size  // what the next index would be

		println("Playing song $song")
		// remember the song as the last selected item
		if (locationStack.size == index || locationStack.getOrNull(index) != song) {
			locationStack.subList(index, locationStack.size).clear()
			locationStack.add(song)
		}

		musicController.playSong(song)
	}

	/**
	 * When a state gets hidden, inform it, so it can disconnect any listeners
	 */
	private fun hide(stateId: Int) {
		pageStack.lastOrNull { it.state.id == stateId }?.hide()
	}
}