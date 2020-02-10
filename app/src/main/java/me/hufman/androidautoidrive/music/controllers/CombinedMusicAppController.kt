package me.hufman.androidautoidrive.music.controllers

import android.util.Log
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.MutableObservable
import me.hufman.androidautoidrive.Observable
import me.hufman.androidautoidrive.music.*
import java.util.*

/**
 * Given a list of Connectors to try, connect to the given MusicAppInfo
 * The Connectors should be sorted to have the most suitable connector at the end of the list
 * For example, a MediaSession controller before the SpotifyAppController
 * This is because the SpotifyAppController can provide better Metadata
 */
class CombinedMusicAppController(connectors: List<MusicAppController.Connector>, val appInfo: MusicAppInfo): MusicAppController {
	private val TAG = "CombinedAppController"
	// Wait up to this time for all the connectors to connect, before doing a browse/search
	val CONNECTION_TIMEOUT = 5000

	companion object {
		class Connector(val connectors: List<MusicAppController.Connector>): MusicAppController.Connector {
			override fun connect(appInfo: MusicAppInfo): Observable<MusicAppController> {
				return MutableObservable<MusicAppController>().also {
					it.value = CombinedMusicAppController(connectors, appInfo)
				}
			}
		}
	}

	// remember the last controller that we browsed or searched through
	private var browseableController: MusicAppController? = null

	var callback: ((MusicAppController) -> Unit)? = null
	private val controllers = connectors.reversed().map {
		it.connect(appInfo).also { pendingController ->
			pendingController.subscribe { freshController ->
				// a controller has connected/disconnected
				if (freshController != null) {
					callback?.invoke(freshController)
					freshController.subscribe { controller ->
						// a controller wants to notify the UI
						callback?.invoke(controller)
					}
				}
			}
		}
	}


	/**
	 * Runs the given command against the first working of the connected controllers
	 */
	private inline fun <R> withController(f: (MusicAppController) -> R): R? {
		for (pendingController in controllers) {
			val controller = pendingController.value ?: continue
			try {
				return f(controller)
			} catch (e: UnsupportedOperationException) {
				// this controller doesn't support it, try the next one
			} catch (e: Exception) {
				// error running the command against this controller, try the next one
				// maybe disconnect it from future attempts, or to reconnect
			}
		}
		return null
	}

	fun isPending(): Boolean {
		return controllers.any {
			it.pending
		}
	}

	fun isConnected(): Boolean {
		return controllers.any {
			it.value != null
		}
	}

	override fun play() {
		withController {
			if (!it.isSupportedAction(MusicAction.PLAY)) {
				throw UnsupportedOperationException()
			}
			it.play()
		}
	}

	override fun pause() {
		withController {
			if (!it.isSupportedAction(MusicAction.PAUSE)) {
				throw UnsupportedOperationException()
			}
			it.pause()
		}
	}

	override fun skipToPrevious() {
		withController {
			if (!it.isSupportedAction(MusicAction.SKIP_TO_PREVIOUS)) {
				throw UnsupportedOperationException()
			}
			it.skipToPrevious()
		}
	}

	override fun skipToNext() {
		withController {
			if (!it.isSupportedAction(MusicAction.SKIP_TO_NEXT)) {
				throw UnsupportedOperationException()
			}
			it.skipToNext()
		}
	}

	override fun seekTo(newPos: Long) {
		withController {
			// some apps don't claim SEEK_TO support but it works perfectly fine, let's try it
			it.seekTo(newPos)
		}
	}

	override fun playSong(song: MusicMetadata) {
		// this command plays a given browse or searched song, so use the last-found browseable controller
		browseableController?.playSong(song)
	}

	override fun playQueue(song: MusicMetadata) {
		withController {
			if (it.getQueue().isEmpty()) {
				throw UnsupportedOperationException()
			}
			it.playQueue(song)
		}
	}

	override fun playFromSearch(search: String) {
		withController {
			if (!it.isSupportedAction(MusicAction.PLAY_FROM_SEARCH)) {
				throw UnsupportedOperationException()
			}
			it.playFromSearch(search)
		}
	}

	override fun customAction(action: CustomAction) {
		withController {
			if (!it.getCustomActions().contains(action)) {
				throw UnsupportedOperationException()
			}
			it.customAction(action)
		}
	}

	override fun getQueue(): List<MusicMetadata> {
		return withController {
			it.getQueue()
		} ?: LinkedList()
	}

	override fun getMetadata(): MusicMetadata? {
		return withController {
			it.getMetadata()
		}
	}

	override fun getPlaybackPosition(): PlaybackPosition {
		return withController {
			it.getPlaybackPosition()
		} ?: PlaybackPosition(true, 0, 0, 0)
	}

	override fun isSupportedAction(action: MusicAction): Boolean {
		return controllers.any {
			it.value?.isSupportedAction(action) == true
		}
	}

	override fun getCustomActions(): List<CustomAction> {
		return withController {
			it.getCustomActions()
		} ?: LinkedList()
	}

	private suspend fun waitforConnect() {
		if (isPending()) {
			for (i in 0..10) {
				delay(CONNECTION_TIMEOUT / 10L)
				if (!isPending()) {
					break
				}
			}
		}
	}

	override suspend fun browse(directory: MusicMetadata?): List<MusicMetadata> {
		// always resume browsing from the previous controller that we were browsing
		val browseableController = this.browseableController
		Log.d(TAG, "Starting browse directory:$directory, browseableController:$browseableController")
		if (directory != null && browseableController != null) {
			Log.d(TAG, "Using same browse controller as before")
			return browseableController.browse(directory)
		}

		waitforConnect()
		// try to find a browseable controller
		for (pendingController in controllers) {
			val controller = pendingController.value ?: continue
			val results = controller.browse(directory)
			// detect empty results and skip this controller
			if (results.isEmpty()) {
				continue
			}
			// make the Play and Browse commands dig through the browse results
			this@CombinedMusicAppController.browseableController = controller
			return results
		}
		Log.i(TAG, "No browseable controllers found")
		return LinkedList()
	}

	override suspend fun search(query: String): List<MusicMetadata>? {
		waitforConnect()
		// try to find a browseable controller
		for (pendingController in controllers) {
			val controller = pendingController.value ?: continue
			val results = controller.search(query) ?: continue
			// we have non-null results, return them as the search results
			// make the Play and Browse commands dig through the search results
			this@CombinedMusicAppController.browseableController = controller
			return results
		}
		return null
	}

	override fun subscribe(callback: (MusicAppController) -> Unit) {
		this.callback = callback
	}

	override fun disconnect() {
		this.callback = null
		controllers.forEach {
			try {
				Log.d(TAG, "Disconnecting ${it.value} controller")
				it.value?.disconnect()
			} catch (e: Exception) {}
		}
	}
}