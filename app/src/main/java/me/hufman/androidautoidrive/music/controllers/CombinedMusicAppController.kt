package me.hufman.androidautoidrive.music.controllers

import android.os.DeadObjectException
import android.util.Log
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.MutableObservable
import me.hufman.androidautoidrive.Observable
import me.hufman.androidautoidrive.music.*
import java.util.*

/**
 * Given a list of Connectors to try, connect to the given MusicAppInfo
 * The Connectors should be sorted to have the most suitable connector at the front of the list
 * For example, a SpotifyAppController before the GenericMusicAppController
 * This is because the SpotifyAppController can provide better Metadata
 */
class CombinedMusicAppController(val controllers: List<Observable<out MusicAppController>>): MusicAppController {
	// Wait up to this time for all the connectors to connect, before doing a browse/search
	val CONNECTION_TIMEOUT = 5000

	companion object {
		const val TAG = "CombinedAppController"
	}
	class Connector(val connectors: List<MusicAppController.Connector>): MusicAppController.Connector {
		override fun connect(appInfo: MusicAppInfo): Observable<CombinedMusicAppController> {
			return MutableObservable<CombinedMusicAppController>().also {
				it.value = CombinedMusicAppController(connectors.map { connector -> connector.connect(appInfo) })
			}
		}
	}

	// remember the last controller that we browsed or searched through
	private var browseableController: MusicAppController? = null

	var callback: ((MusicAppController) -> Unit)? = null

	init {
		controllers.forEach { pendingController ->
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
	fun <R> withController(f: (MusicAppController) -> R): R? {
		for (pendingController in controllers) {
			val controller = pendingController.value ?: continue
			try {
				return f(controller)
			} catch (e: DeadObjectException) {
				// raise the disconnect to the main MusicController
				throw e
			} catch (e: UnsupportedOperationException) {
				// this controller doesn't support it, try the next one
			} catch (e: Exception) {
				// error running the command against this controller, try the next one
				// maybe disconnect it from future attempts, or to reconnect
				Log.w(TAG, "Received exception from controller $controller: $e")
			}
		}
		return null
	}

	fun isPending(): Boolean {
		return controllers.any {
			it.pending
		}
	}

	override fun isConnected(): Boolean {
		return controllers.any {
			it.value?.isConnected() == true
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
		// check for exact matches
		for (pendingController in controllers) {
			val controller = pendingController.value ?: continue
			val controllerActions = controller.getCustomActions()
			if (controllerActions.any { it === action }) {
				controller.customAction(action)
				return
			}
		}
		// check for equality
		for (pendingController in controllers) {
			val controller = pendingController.value ?: continue
			val controllerActions = controller.getCustomActions()
			if (controllerActions.any { it == action }) {
				controller.customAction(action)
				return
			}
		}
	}

	override fun getQueue(): List<MusicMetadata> {
		return withController {
			val queue = it.getQueue()
			if (queue.isEmpty()) {
				throw UnsupportedOperationException()
			}
			queue
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
		val actions = ArrayList<CustomAction>()
		val nameIndices = HashMap<String, Int>()    // points to the array slot with the given action
		for (pendingController in controllers) {
			val controller = pendingController.value ?: continue
			val controllerActions = controller.getCustomActions()
			controllerActions.forEach { controllerAction ->
				val index = nameIndices[controllerAction.action]
				if (index != null) {
					if (actions[index].icon == null && controllerAction.icon != null) {
						actions[index] = controllerAction
					}
				} else {
					nameIndices[controllerAction.action] = actions.size
					actions.add(controllerAction)
				}
			}
		}
		return actions
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
		if (directory != null && browseableController != null) {
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