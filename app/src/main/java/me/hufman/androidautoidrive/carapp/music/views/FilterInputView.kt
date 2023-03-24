package me.hufman.androidautoidrive.carapp.music.views

import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import io.bimmergestalt.idriveconnectkit.rhmi.VisibleCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.hufman.androidautoidrive.CarThreadExceptionHandler
import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.carapp.InputState
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.music.MusicMetadata
import kotlin.coroutines.CoroutineContext

class FilterInputView(val state: RHMIState,
                      private val browseController: BrowsePageController,
                      private val browsePageModel: BrowsePageModel): CoroutineScope {
	val FILTERRESULT_LOADING = MusicMetadata(mediaId="__LOADING__", title=L.MUSIC_BROWSE_LOADING)
	val FILTERRESULT_EMPTY = MusicMetadata(mediaId="__EMPTY__", title=L.MUSIC_BROWSE_EMPTY)

	override val coroutineContext: CoroutineContext
		get() = Dispatchers.IO + CarThreadExceptionHandler

	var loadingJob: Job? = null
	var musicList: List<MusicMetadata> = emptyList()
	var inputState: InputState<MusicMetadata>? = null

	fun show() {
		// make sure the deferred contents are loaded
		if (browsePageModel.contents.isCompleted) {
			@Suppress("EXPERIMENTAL_API_USAGE")
			musicList = browsePageModel.contents.getCompleted() ?: emptyList()
		} else {
			loadingJob = launch(Dispatchers.IO) {
				musicList = browsePageModel.contents.await() ?: emptyList()
				// update suggestions, if any input exists
				inputState?.input?.also {
					inputState?.onEntry(it)
				}
			}
		}

		// cancel any pending loading when hidden
		state.visibleCallback = VisibleCallback { visible ->
			if (!visible) loadingJob?.cancel()
		}

		// prepare the Input state
		inputState = object: InputState<MusicMetadata>(state) {
			override fun onEntry(input: String) {
				if (!browsePageModel.contents.isCompleted) {
					// still loading
					sendSuggestions(listOf(FILTERRESULT_LOADING))
				} else {
					val results = input.lowercase().let { inputLower ->
						musicList.asSequence().map {
							Pair(it, UnicodeCleaner.clean((it.title ?: "") + " " + (it.artist ?: "")).lowercase())
						}.let {
							it.filter { meta ->
								meta.second.split(Regex("\\s+")).any { word ->
									word.startsWith(inputLower)
								}
							} + it.filter { meta ->
								meta.second.contains(inputLower)
							}
						}.map { it.first }.distinct().take(15).toList()
					}
					if (results.isEmpty()) {
						sendSuggestions(listOf(FILTERRESULT_EMPTY))
					} else {
						sendSuggestions(results)
					}
				}
			}

			override fun onSelect(item: MusicMetadata, index: Int) {
				if (item == FILTERRESULT_EMPTY || item == FILTERRESULT_LOADING) {
					// invalid selection, don't change states
					inputComponent.getSuggestAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = 0
					throw RHMIActionAbort()
				}
				browsePageModel.previouslySelected = item  // update the selection state for future redraws
				browseController.onListSelection(inputComponent.getSuggestAction()?.asHMIAction(), item)
			}

			override fun convertRow(row: MusicMetadata): String {
				return UnicodeCleaner.clean(row.title ?: "")
			}
		}
	}
}