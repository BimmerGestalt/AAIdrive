package me.hufman.androidautoidrive.carapp.music.views

import android.util.Log
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import io.bimmergestalt.idriveconnectkit.rhmi.VisibleCallback
import com.google.gson.Gson
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.CarThreadExceptionHandler
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
	companion object {
		val SEARCH_RESULT_SEARCHING = MusicMetadata(mediaId="__SEARCHING__", title=L.MUSIC_BROWSE_SEARCHING)
		val SEARCH_RESULT_EMPTY = MusicMetadata(mediaId="__EMPTY__", title=L.MUSIC_BROWSE_EMPTY)
		val SEARCH_RESULT_VIEW_FULL_RESULTS = MusicMetadata(mediaId="__VIEWFULLRESULTS__", title=L.MUSIC_SEARCH_RESULTS_VIEW_FULL_RESULTS)
		val SEARCH_RESULT_ELLIPSIS = MusicMetadata(mediaId="__MORERESULTS__", title=L.MUSIC_SEARCH_RESULTS_ELLIPSIS)

		const val SEARCH_HISTORY_ITEM_MEDIAID = "SearchHistoryItemMediaId"
		const val SEARCH_HISTORY_QUERY_MAX_COUNT = 8
	}

	override val coroutineContext: CoroutineContext
		get() = Dispatchers.IO + CarThreadExceptionHandler
	private var searchJob: Job? = null
	private var searchQueryHistory: MutableList<String> = LinkedList()
	private val gson: Gson = Gson()

	init {
		val searchQueryHistoryJson = musicController.appSettings[AppSettings.KEYS.MUSIC_SEARCH_QUERY_HISTORY]
		if (searchQueryHistoryJson.isNotBlank()) {
			searchQueryHistory = gson.fromJson(searchQueryHistoryJson, Array<String>::class.java).toMutableList()
		}
	}

	fun show() {
		// cancel any pending loading when hidden
		state.visibleCallback = VisibleCallback { visible ->
			if (!visible) searchJob?.cancel()
		}

		object : InputState<MusicMetadata>(state) {
			val MAX_RETRIES = 2
			var deferredSearchResults: Deferred<List<MusicMetadata>?> = CompletableDeferred(emptyList())
			val isSpotify = musicController.currentAppInfo?.name == "Spotify"

			override fun onEntry(input: String) {
				// keep the suggestions in sync with the entered input
				synchronized(this) {
					suggestions.clear()
					sendSuggestions(emptyList())
				}

				if (input.isNotEmpty()) {
					search(input)
				} else {
					super.sendSuggestions(searchQueryHistory.map {
						MusicMetadata(mediaId=SEARCH_HISTORY_ITEM_MEDIAID, title=it)
					})
				}
			}

			override fun onInput(letter: String) {
				when (letter) {
					"delall" -> this.input = ""
					"del" -> this.input = this.input.dropLast(1)
					else -> if (letter.length > 1) {
						// workaround for when result model has been set to a non-empty value the "letter" that is passed into the SpellerCallback is the entire modified input string
						this.input = letter
					} else {
						this.input += letter
					}
				}
				inputComponent.getResultModel()?.asRaDataModel()?.value = this.input
				onEntry(this.input)
			}

			/**
			 * Performs the search on the input asynchronously and returns the list of [MusicMetadata]
			 * results. If the search times out then it is retried for the specific amount of times
			 * before returning a list containing only the [SEARCH_RESULT_EMPTY] item.
			 */
			suspend fun getSearchResults(input: String, retries: Int): List<MusicMetadata>? {
				if (retries <= 0) {
					Log.d(TAG, "Too many search retries for query: \"$input\"")
					return listOf(SEARCH_RESULT_EMPTY)
				}

				val deferredResults = musicController.searchAsync(input)
				val suggestions = deferredResults.awaitPending(BrowsePageView.LOADING_TIMEOUT) {
					Log.d(TAG, "Searching ${musicController.currentAppInfo?.name} for \"$input\" timed out, retry attempts remaining: ${retries-1}")
					return getSearchResults(input, retries-1)
				}

				deferredSearchResults = deferredResults
				Log.d(TAG, "Results for \"$input\" updated")

				return suggestions
			}

			/**
			 * Trims the provided search suggestion to 29 entries as the suggestModel can only show
			 * the first 32 entries correctly, leaving room for the other potential buttons.
			 */
			fun trimSuggestions(suggestions: List<MusicMetadata>?): List<MusicMetadata> {
				return if (suggestions != null) {
					if (suggestions.size > 29) {
						suggestions.subList(0, 28) + listOf(SEARCH_RESULT_ELLIPSIS)
					} else {
						suggestions
					}
				} else {
					LinkedList()
				}
			}

			/**
			 * Searches for the specified input in a search [Job] and updates the suggestion model
			 * with the returned results.
			 */
			fun search(input: String) {
				searchJob?.cancel()
				searchJob = launch(Dispatchers.IO) {
					sendSuggestions(listOf(SEARCH_RESULT_SEARCHING))
					val suggestions = getSearchResults(input, MAX_RETRIES)

					//update suggestions if search job hasn't been cancelled
					if (isActive) {
						val trimmedSuggestions = trimSuggestions(suggestions)
						sendSuggestions(trimmedSuggestions)
					}

				}
			}

			override fun sendSuggestions(newSuggestions: List<MusicMetadata>) {
				val suggestions = if (musicController.isSupportedAction(MusicAction.PLAY_FROM_SEARCH) && !isSpotify) {
					listOf(BrowseView.SEARCHRESULT_PLAY_FROM_SEARCH) + newSuggestions
				} else {
					newSuggestions
				}
				val fullSuggestions = if (newSuggestions.isNotEmpty() && newSuggestions[0] != SEARCH_RESULT_SEARCHING && newSuggestions[0] != SEARCH_RESULT_EMPTY) {
					listOf(SEARCH_RESULT_VIEW_FULL_RESULTS) + suggestions
				} else {
					suggestions
				}
				super.sendSuggestions(fullSuggestions)
			}

			override fun onSelect(item: MusicMetadata, index: Int) {
				if (item == SEARCH_RESULT_EMPTY || item == SEARCH_RESULT_SEARCHING) {
					// invalid selection, don't change states
					inputComponent.getSuggestAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = 0
					throw RHMIActionAbort()
				} else if (item == BrowseView.SEARCHRESULT_PLAY_FROM_SEARCH) {
					updateSearchQueryHistory(this.input)
					browseController.playFromSearch(inputComponent.getSuggestAction()?.asHMIAction(), this.input)
				} else if (item == SEARCH_RESULT_VIEW_FULL_RESULTS || item == SEARCH_RESULT_ELLIPSIS) {
					updateSearchQueryHistory(this.input)
					browseController.showSearchResults(deferredSearchResults, inputComponent.getSuggestAction()?.asHMIAction())
				} else if (item.mediaId == SEARCH_HISTORY_ITEM_MEDIAID) {
					updateSearchQueryHistory(item.title!!)
					inputComponent.getSuggestAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = inputComponent.id
					inputComponent.getResultModel()?.asRaDataModel()?.value = item.title
				} else {
					updateSearchQueryHistory(this.input)
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
				updateSearchQueryHistory(this.input)
				if(!isSpotify && deferredSearchResults.isCompleted && deferredSearchResults.getCompleted()?.isEmpty() == true) {
					browseController.playFromSearch(inputComponent.getResultAction()?.asHMIAction(), this.input)
				} else if (!deferredSearchResults.isCompleted || deferredSearchResults.getCompleted()?.isNotEmpty() == true) {
					browseController.showSearchResults(deferredSearchResults, inputComponent.getResultAction()?.asHMIAction())
				}
			}

			/**
			 * Updates the search query history by adding the current search query input to the
			 * search query history collection or moves it to the top if has been searched before
			 * and saves the collection to the [AppSettings].
			 *
			 * Note: The collection can only have a maximum of [SEARCH_HISTORY_QUERY_MAX_COUNT] total
			 * queries. If the collection is at max capacity and a new unique search query is being
			 * added the oldest search query will be dropped from the collection.
			 */
			private fun updateSearchQueryHistory(input: String) {
				if (searchQueryHistory.contains(input)) {
					searchQueryHistory.remove(input)
				}
				if (searchQueryHistory.size == SEARCH_HISTORY_QUERY_MAX_COUNT) {
					searchQueryHistory.remove(searchQueryHistory.last())
				}
				searchQueryHistory.add(0, input)
				musicController.appSettings[AppSettings.KEYS.MUSIC_SEARCH_QUERY_HISTORY] = gson.toJson(searchQueryHistory)
			}
		}
	}
}