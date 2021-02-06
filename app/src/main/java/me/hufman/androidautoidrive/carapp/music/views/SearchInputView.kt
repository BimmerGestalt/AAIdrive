package me.hufman.androidautoidrive.carapp.music.views

import android.util.Log
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.carapp.InputState
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.music.MusicAction
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.utils.awaitPending
import me.hufman.idriveconnectionkit.rhmi.RHMIState
import me.hufman.idriveconnectionkit.rhmi.VisibleCallback
import java.util.*
import kotlin.coroutines.CoroutineContext

class SearchInputView(val state: RHMIState,
                      private val musicController: MusicController,
                      private val browseController: BrowsePageController): CoroutineScope {
	val SEARCHRESULT_SEARCHING = MusicMetadata(mediaId="__SEARCHING__", title=L.MUSIC_BROWSE_SEARCHING)
	val SEARCHRESULT_EMPTY = MusicMetadata(mediaId="__EMPTY__", title=L.MUSIC_BROWSE_EMPTY)

	override val coroutineContext: CoroutineContext
		get() = Dispatchers.IO
	private var searchJob: Job? = null

	fun show() {
		// cancel any pending loading when hidden
		state.visibleCallback = VisibleCallback { visible ->
			if (!visible) searchJob?.cancel()
		}

		object : InputState<MusicMetadata>(state) {
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
						val suggestionsDeferred = musicController.searchAsync(input)
						val suggestions = suggestionsDeferred.awaitPending(BrowsePageView.LOADING_TIMEOUT) {
							Log.d(TAG, "Searching ${musicController.currentAppInfo?.name} for \"$input\" timed out, retrying")
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
				val fullSuggestions = if (musicController.isSupportedAction(MusicAction.PLAY_FROM_SEARCH)) {
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
					browseController.playFromSearch(inputComponent.getSuggestAction()?.asHMIAction(), this.input)
				} else {
					browseController.onListSelection(inputComponent.getSuggestAction()?.asHMIAction(), item)
				}
			}

			override fun convertRow(row: MusicMetadata): String {
				return if (row.subtitle != null) {
					UnicodeCleaner.clean("${row.title}\n${row.subtitle}")
				} else {
					UnicodeCleaner.clean(row.title ?: "")
				}
			}
		}
	}
}