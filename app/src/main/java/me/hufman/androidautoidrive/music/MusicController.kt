package me.hufman.androidautoidrive.music

import android.content.Context
import android.os.DeadObjectException
import android.os.Handler
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.music.controllers.CombinedMusicAppController
import me.hufman.androidautoidrive.music.controllers.MusicAppController
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import java.lang.Runnable
import java.util.*
import kotlin.coroutines.CoroutineContext

class MusicController(val context: Context, val handler: Handler): CoroutineScope {
	override val coroutineContext: CoroutineContext
		get() = handler.asCoroutineDispatcher()

	// async jobs
	var browseJob: Job? = null
	var searchJob: Job? = null

	companion object {
		private const val TAG = "MusicController"

		// how often to reconnect to an app if it returns NULL metadata
		private const val RECONNECT_TIMEOUT = 1000

		// disable autoswitch for a time after the user picks an app manually
		private const val DISABLE_AUTOSWITCH_TIMEOUT = 5000
	}

	val musicSessions = MusicSessions(context)
	val connectors = listOf(
			SpotifyAppController.Connector(context),
			MusicBrowser.Connector(context, handler),
			musicSessions.Connector(context)
	)
	val connector = CombinedMusicAppController.Connector(connectors)

	var disableAutoswitchUntil = 0L
	var lastConnectTime = 0L
	var currentAppInfo: MusicAppInfo? = null
	var currentAppController: MusicAppController? = null

	var listener: Runnable? = null
	var desiredPlayback = false  // if we should start playback as soon as connected
	var triggeredPlayback = false   // whether we have triggered playback on a fresh connection

	// handles manual rewinding/fastforwarding
	val seekingController = SeekingController(context, handler, this)


	init {
		handler.post { scheduleRedrawProgress() }
	}

	/**
	 * Run on the handler's thread, and don't crash for RPC disconnections
	 */
	private inline fun asyncRpc(crossinline f: () -> Unit) {
		handler.post {
			try {
				f()
			} catch (e: DeadObjectException) {
				// the controller disconnected and threw an error
				// but we don't know which one, so we can't clear a connection
			}
		}
	}

	/**
	 * Run the given task against the connected controller
	 * Errors will trigger an attempt to run against the other controller, if connected
	 * It will run the task with null if no controllers are connected
	 * This method does not switch threads, keep RPC context in mind
	 */
	private inline fun <R> withController(f: (MusicAppController) -> R): R? {
		try {
			val currentAppController = currentAppController
			if (currentAppController != null) {
				return f(currentAppController)
			}
		} catch (e: DeadObjectException) {
			// the controller disconnected
			Log.i(TAG, "Received exception from $currentAppController, disconnecting")
			disconnectApp(false)
		}
		return null
	}
	/**
	 * Run this controller task on the handler's thread, and don't crash for RPC disconnections
	 * It will apply to the controller that is connected when the task fires
	 */
	private inline fun asyncControl(crossinline f: (MusicAppController) -> Unit) {
		handler.post {
			withController(f)
		}
	}

	fun connectAppManually(app: MusicAppInfo) {
		disableAutoswitchUntil = System.currentTimeMillis() + DISABLE_AUTOSWITCH_TIMEOUT
		connectApp(app)
	}

	fun connectAppAutomatically(app: MusicAppInfo) {
		if (System.currentTimeMillis() > disableAutoswitchUntil) {
			connectApp(app)
		}
	}

	private fun connectApp(app: MusicAppInfo) {
		val previousAppInfo = currentAppInfo
		currentAppInfo = app
		asyncRpc {
			val switchApp = currentAppInfo != previousAppInfo
			val needsReconnect = !isConnected()
			if (switchApp || needsReconnect) {
				Log.i(TAG, "Switching current app connection from $currentAppInfo to $app")
				disconnectApp(pause = switchApp)

				triggeredPlayback = false
				val controller = connector.connect(app).value
				if (controller == null) {
					Log.e(TAG, "Unable to connect to CombinedMusicAppController, this should never happen")
				} else {
					controller.subscribe {
						if (controller.isConnected() && desiredPlayback && !triggeredPlayback) {
							controller.play()
							triggeredPlayback = true
						}
						scheduleRedraw()
					}
					currentAppController = controller
					saveDesiredApp(app)
				}
			}
		}
	}

	/** Remember this app as the last one to play */
	fun saveDesiredApp(app: MusicAppInfo) {
		AppSettings.saveSetting(context, AppSettings.KEYS.AUDIO_DESIRED_APP, app.packageName)
	}

	/** Return the packageName of the last app to play */
	fun loadDesiredApp(): String {
		return AppSettings[AppSettings.KEYS.AUDIO_DESIRED_APP]
	}

	fun isConnected(): Boolean {
		return currentAppController?.isConnected() == true
	}

	fun disconnectApp(pause: Boolean = true) {
		Log.d(TAG, "Disconnecting $currentAppController")
		// trigger a pause of the current connected app
		if (pause) {
			pauseSync()
		}

		// then clear out the saved controller object, to defer future play() calls
		currentAppController?.disconnect()
		currentAppController = null
	}

	/* Controls */
	fun play() {
		desiredPlayback = true
		asyncControl { controller ->
			controller.play()
		}
	}
	fun playFromSearch(search: String) = asyncControl { controller ->
		controller.playFromSearch(search)
	}
	fun pause() = asyncControl { controller ->
		desiredPlayback = false
		controller.pause()
	}
	fun pauseSync() = withController { controller -> // all calls are already in the handler thread, don't go async
		controller.pause()
	}
	fun skipToPrevious() = asyncControl { controller ->
		controller.skipToPrevious()
	}
	fun skipToNext() = asyncControl { controller ->
		controller.skipToNext()
	}
	fun startRewind() {
		seekingController.startRewind()
	}
	fun startFastForward() {
		seekingController.startFastForward()
	}
	fun stopSeeking() {
		seekingController.stopSeeking()
	}
	fun seekTo(newPos: Long) = asyncControl { controller ->
		controller.seekTo(newPos)
	}

	fun playSong(song: MusicMetadata) = asyncControl { controller ->
		controller.playSong(song)
	}

	fun playQueue(song: MusicMetadata) = asyncControl { controller ->
		controller.playQueue(song)
	}

	fun customAction(action: CustomAction) = asyncControl { controller ->
		if (seekingController.seekingActions.contains(action)) {
			seekingController.seekAction(action)
		} else {
			controller.customAction(action)
		}
	}

	fun toggleShuffle() = asyncControl { controller ->
		controller.toggleShuffle()
	}

	fun isShuffling(): Boolean {
		return withController { controller ->
			return controller.isShuffling()
		} ?: false
	}

	fun toggleRepeat() = asyncControl { controller ->
		controller.toggleRepeat()
	}

	fun getRepeatMode(): RepeatMode {
		return withController { controller ->
			return controller.getRepeatMode()
		} ?: RepeatMode.OFF
	}

	fun browseAsync(directory: MusicMetadata?): Deferred<List<MusicMetadata>> {
		val results: CompletableDeferred<List<MusicMetadata>> = CompletableDeferred()
		withController { controller ->
			browseJob?.cancel()
			browseJob = launch {
				results.complete(controller.browse(directory))
			}
		}
		return results
	}

	fun searchAsync(query: String): Deferred<List<MusicMetadata>?> {
		val results: CompletableDeferred<List<MusicMetadata>?> = CompletableDeferred()
		withController { controller ->
			searchJob?.cancel()
			searchJob = launch {
				results.complete(controller.search(query))
			}
		}
		return results
	}

	/* Current state */
	/** Gets the current queue */
	fun getQueue(): QueueMetadata? {
		return withController { controller ->
			return controller.getQueue()
		}
	}
	/** Gets the current song's title and other metadata */
	fun getMetadata(): MusicMetadata? {
		return withController { controller ->
			return controller.getMetadata()
		}
	}
	/** Gets the song's playback position */
	fun getPlaybackPosition(): PlaybackPosition {
		return withController { controller ->
			controller.getPlaybackPosition()
		} ?: PlaybackPosition(true, 0, 0, 0)
	}

	fun getCustomActions(): List<CustomAction> {
		val appActions = withController { controller ->
			controller.getCustomActions()
		} ?: LinkedList()
		return if (((getMetadata()?.duration ?: -1) > 0) || isSupportedAction(MusicAction.SEEK_TO)) {
			// Rocket Player claims to not support SEEK_TO
			// Spotify's duration sometimes goes missing
			appActions + seekingController.seekingActions
		} else {
			appActions
		}
	}

	fun isSupportedAction(action: MusicAction): Boolean {
		return withController { controller ->
			return controller.isSupportedAction(action)
		} ?: false
	}

	val redrawProgressTask = Runnable {
		redrawProgress()
	}
	fun redrawProgress() {
		handler.removeCallbacks(redrawProgressTask)

		listener?.run()
		scheduleRedrawProgress()

		// detect buggy youtube MediaController
		assertPlayingMetadata()
	}
	fun scheduleRedrawProgress() {
		val position = getPlaybackPosition()
		if (position.playbackPaused) {
			handler.postDelayed(redrawProgressTask, 500)
		} else {
			// the time until the next second interval
			val delay = 1000 - position.getPosition() % 1000
			handler.postDelayed(redrawProgressTask, delay)
		}
	}

	val redrawTask = Runnable {
		listener?.run()

		// detect buggy youtube MediaController
		assertPlayingMetadata()
	}
	fun scheduleRedraw() {
		handler.removeCallbacks(redrawTask)
		handler.postDelayed(redrawTask, 100)
	}

	/** If the current app is playing, make sure the metadata is valid */
	fun assertPlayingMetadata() = withController { controller ->
		val metadata = controller.getMetadata()
		val appInfo = currentAppInfo
		if (appInfo?.packageName == "com.google.android.youtube" && metadata == null && System.currentTimeMillis() > lastConnectTime + RECONNECT_TIMEOUT) {
			Log.w(TAG, "Detected NULL metadata for an app, reconnecting")
			lastConnectTime = System.currentTimeMillis()
			disconnectApp(false)
			connectApp(appInfo)
		}
	}
}