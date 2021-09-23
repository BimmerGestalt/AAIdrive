package me.hufman.androidautoidrive.carapp.music.views

import android.util.Log
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import io.bimmergestalt.idriveconnectkit.rhmi.VisibleCallback
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.carapp.InputState
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.music.MusicAction
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.utils.awaitPending
import java.util.*
import kotlin.coroutines.CoroutineContext

class SearchInputView(val state: RHMIState,
                      private val musicController: MusicController,
                      private val browseController: BrowsePageController): CoroutineScope {
	val SEARCHRESULT_SEARCHING = MusicMetadata(mediaId="__SEARCHING__", title=L.MUSIC_BROWSE_SEARCHING)
	val SEARCHRESULT_EMPTY = MusicMetadata(mediaId="__EMPTY__", title=L.MUSIC_BROWSE_EMPTY)
	val SEARCHRESULT_VIEW_FULL_RESULTS = MusicMetadata(mediaId="__VIEWFULLRESULTS__", title=L.MUSIC_SEARCH_RESULTS_VIEW_FULL_RESULTS)
	val SEARCHRESULT_ELIPSIS = MusicMetadata(mediaId="__MORERESULTS__", title=L.MUSIC_SEARCH_RESULTS_ELLIPSIS)

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
			var deferredSearchResults: Deferred<List<MusicMetadata>?> = CompletableDeferred(emptyList())
			val isSpotify = musicController.currentAppInfo?.name == "Spotify"

			override fun onEntry(input: String) {
				searchRetries = MAX_RETRIES

				// keep the suggestions in sync with the entered input
				synchronized(this) {
					suggestions.clear()
					sendSuggestions(emptyList())
				}

				search(input)
			}

			fun search(input: String) {
				if (input.length >= 2 && searchRetries > 0) {
					searchJob?.cancel()
					searchJob = launch(Dispatchers.IO) {
						sendSuggestions(listOf(SEARCHRESULT_SEARCHING))
						deferredSearchResults = musicController.searchAsync(input)
						val suggestions = deferredSearchResults.awaitPending(BrowsePageView.LOADING_TIMEOUT) {
							Log.d(TAG, "Searching ${musicController.currentAppInfo?.name} for \"$input\" timed out, retrying")
							searchRetries -= 1
							search(input)
							return@launch
						}

						// The suggestModel can only show the first 32 entries correctly so show 29 entries leaving room for the other potential buttons
						val trimmedSuggestions = if (suggestions != null) {
							if (suggestions.size > 29) {
								suggestions.subList(0, 28) + listOf(SEARCHRESULT_ELIPSIS)
							} else {
								suggestions
							}
						} else {
							LinkedList()
						}

						sendSuggestions(trimmedSuggestions)
					}
				} else if (searchRetries <= 0) {
					// too many retries
					sendSuggestions(listOf(SEARCHRESULT_EMPTY))
				}
			}

			override fun sendSuggestions(newSuggestions: List<MusicMetadata>) {
				val suggestions = if (musicController.isSupportedAction(MusicAction.PLAY_FROM_SEARCH) && !isSpotify) {
					listOf(BrowseView.SEARCHRESULT_PLAY_FROM_SEARCH) + newSuggestions
				} else {
					newSuggestions
				}
				val fullSuggestions = if (newSuggestions.isNotEmpty() && newSuggestions[0] != SEARCHRESULT_SEARCHING && newSuggestions[0] != SEARCHRESULT_EMPTY) {
					listOf(SEARCHRESULT_VIEW_FULL_RESULTS) + suggestions
				} else {
					suggestions
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
				} else if (item == SEARCHRESULT_VIEW_FULL_RESULTS) {
					browseController.showSearchResults(deferredSearchResults, inputComponent.getSuggestAction()?.asHMIAction())
				} else if (item == SEARCHRESULT_ELIPSIS) {
					browseController.showSearchResults(deferredSearchResults, inputComponent.getSuggestAction()?.asHMIAction())
				} else {
					browseController.onListSelection(inputComponent.getSuggestAction()?.asHMIAction(), item)
				}
			}

			override fun convertRow(row: MusicMetadata): String {
				return if (row.subtitle != null) {
					UnicodeCleaner.clean("${row.title} - ${row.subtitle} - ${row.artist}")
				} else {
					UnicodeCleaner.clean(row.title ?: "")
				}
			}

			@Suppress("EXPERIMENTAL_API_USAGE")
			override fun onOk() {
				if(!isSpotify && deferredSearchResults.isCompleted && deferredSearchResults.getCompleted()?.isEmpty() == true) {
					browseController.playFromSearch(inputComponent.getResultAction()?.asHMIAction(), this.input)
				} else if (!deferredSearchResults.isCompleted || deferredSearchResults.getCompleted()?.isNotEmpty() == true) {
					browseController.showSearchResults(deferredSearchResults, inputComponent.getResultAction()?.asHMIAction())
				}
			}
		}
	}
}