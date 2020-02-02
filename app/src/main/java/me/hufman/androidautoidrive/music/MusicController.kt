package me.hufman.androidautoidrive.music

import android.content.Context
import android.os.DeadObjectException
import android.os.Handler
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.music.controllers.GenericMusicAppController
import me.hufman.androidautoidrive.music.controllers.MusicAppController
import java.util.*

class MusicController(val context: Context, val handler: Handler) {
	companion object {
		private const val TAG = "MusicController"

		// how many milliseconds per half-second interval to seek, at each button-hold time threshold
		private val SEEK_THRESHOLDS = listOf(
				0 to 4000,
				2000 to 7000,
				5000 to 13000,
				8000 to 30000
		)

		// how often to reconnect to an app if it returns NULL metadata
		private const val RECONNECT_TIMEOUT = 1000
	}

	var lastConnectTime = 0L
	var currentAppInfo: MusicAppInfo? = null
	var currentAppController: MusicAppController? = null
	private var musicBrowser: MusicBrowser? = null
	val musicSessions = MusicSessions(context)

	private val controllerCallback = Callback()
	var listener: Runnable? = null
	var desiredPlayback = false  // if we should start playback as soon as connected

	// handles manual rewinding/fastforwarding
	private var startedSeekingTime: Long = 0    // determine how long the user has been holding the seek button
	private var seekingDirectionForward = true
	private val seekingRunnable = object : Runnable {
		override fun run() {
			if (startedSeekingTime > 0) {
				val holdTime = System.currentTimeMillis() - startedSeekingTime
				val seekTime = SEEK_THRESHOLDS.lastOrNull { holdTime >= it.first }?.second ?: 2000
				val curPos = getPlaybackPosition().getPosition()
				val newPos = curPos + if (seekingDirectionForward) { 1 } else { -1 } * seekTime
				// handle seeking past beginning of song
				if (newPos < 0) {
					skipToPrevious()
					startedSeekingTime = 0  // cancel seeking, because skipToPrevious starts at the beginning of the previous song
				} else {
					seekTo(newPos)
					handler.postDelayed(this, 300)
				}
			}
		}
	}


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
	private inline fun <R> withController(f: (MusicAppController?) -> R): R {
		try {
			return f(currentAppController)
		} catch (e: DeadObjectException) {
			// the controller disconnected
			disconnectApp(false)
		}
		return f(null)
	}
	/**
	 * Run this controller task on the handler's thread, and don't crash for RPC disconnections
	 * It will apply to the controller that is connected when the task fires
	 */
	private inline fun asyncControl(crossinline f: (MusicAppController?) -> Unit) {
		handler.post {
			withController(f)
		}
	}

	fun connectApp(app: MusicAppInfo) = asyncRpc {
		val switchApp = currentAppInfo != app
		val needsReconnect = currentAppController == null
		if (switchApp || needsReconnect) {
			Log.i(TAG, "Switching current app connection from $currentAppInfo to $app")
			disconnectApp(pause = switchApp)

			// try to connect to an existing session
			musicSessions.connectApp(app)
			val sessionController = musicSessions.mediaController
			if (sessionController != null) {
				sessionController.registerCallback(controllerCallback, handler)
				currentAppController?.disconnect()
				currentAppController = GenericMusicAppController(context, sessionController, null)
				if (desiredPlayback) {
					Log.i(TAG, "Resuming playback on new music connection")
					play()
				}
				scheduleRedraw()
			}

			// try to connect to the media browser
			lastConnectTime = System.currentTimeMillis()
			musicBrowser = MusicBrowser(context, handler, app)
			musicBrowser?.listener = Runnable {
				Log.d(TAG, "Notified of new music browser connection")
				musicBrowser?.mediaController?.registerCallback(controllerCallback, handler)
				if (desiredPlayback) {
					Log.i(TAG, "Resuming playback on new music browser connection")
					play()
				}

				val musicBrowser = musicBrowser
				val browserController = musicBrowser?.mediaController
				if (browserController != null) {
					currentAppController?.disconnect()
					currentAppController = GenericMusicAppController(context, browserController, musicBrowser)
				}
				scheduleRedraw()
				saveDesiredApp(app)
			}
		}
		currentAppInfo = app
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
		return currentAppController != null
	}

	fun disconnectApp(pause: Boolean = true) {
		musicBrowser?.mediaController?.unregisterCallback(controllerCallback)
		musicSessions.mediaController?.unregisterCallback(controllerCallback)

		// trigger a pause of the current connected app
		if (pause) {
			pauseSync()
		}

		// then clear out the saved controller object, to defer future play() calls
		musicBrowser?.disconnect()
		musicBrowser = null
		musicSessions.mediaController = null
		currentAppController?.disconnect()
		currentAppController = null
	}

	/* Controls */
	fun play() = asyncControl { controller ->
		desiredPlayback = true
		if (controller == null) {
			Log.w(TAG, "Play request but no active music app connection")
		} else {
			if (controller.getPlaybackPosition().playbackPaused) {
				controller.play()
			}
		}
	}
	fun playFromSearch(search: String) = asyncControl { controller ->
		controller?.playFromSearch(search)
	}
	fun pause() = asyncControl { controller ->
		desiredPlayback = false
		if (controller?.getPlaybackPosition()?.playbackPaused == false) {
			controller.pause()
		}
	}
	fun pauseSync() = withController { controller -> // all calls are already in the handler thread, don't go async
		controller?.pause()
	}
	fun skipToPrevious() = asyncControl { controller ->
		controller?.skipToPrevious()
	}
	fun skipToNext() = asyncControl { controller ->
		controller?.skipToNext()
	}
	fun startRewind() {
		startedSeekingTime = System.currentTimeMillis()
		seekingDirectionForward = false
		seekingRunnable.run()
	}
	fun startFastForward() {
		startedSeekingTime = System.currentTimeMillis()
		seekingDirectionForward = true
		seekingRunnable.run()
	}
	fun stopSeeking() = asyncControl { controller ->
		startedSeekingTime = 0
		play()
	}
	fun seekTo(newPos: Long) = asyncControl { controller ->
		controller?.seekTo(newPos)
	}

	fun playSong(song: MusicMetadata) = asyncControl { controller ->
		controller?.playSong(song)
	}

	fun playQueue(song: MusicMetadata) = asyncControl { controller ->
		controller?.playQueue(song)
	}

	fun customAction(action: CustomAction) = asyncControl { controller ->
		controller?.customAction(action)
	}

	fun browseAsync(directory: MusicMetadata?): Deferred<List<MusicMetadata>> = withController { controller ->
		return controller?.browseAsync(directory) ?: CompletableDeferred(LinkedList())
	}

	fun searchAsync(query: String): Deferred<List<MusicMetadata>> = withController { controller ->
		return controller?.searchAsync(query) ?: CompletableDeferred(LinkedList())
	}

	/* Current state */
	/** Gets the current queue */
	fun getQueue(): List<MusicMetadata>? = withController { controller ->
		return controller?.getQueue()
	}
	/** Gets the current song's title and other metadata */
	fun getMetadata(): MusicMetadata? = withController { controller ->
		return controller?.getMetadata()
	}
	/** Gets the song's playback position */
	fun getPlaybackPosition(): PlaybackPosition = withController { controller ->
		return controller?.getPlaybackPosition() ?: PlaybackPosition(true, 0, 0, 0)
	}

	fun getCustomActions(): List<CustomAction> = withController { controller ->
		return controller?.getCustomActions() ?: LinkedList()
	}

	fun isSupportedAction(action: MusicAction): Boolean = withController { controller ->
		return controller?.isSupportedAction(action) ?: false
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
		val metadata = controller?.getMetadata()
		if (controller != null && metadata == null && System.currentTimeMillis() > lastConnectTime + RECONNECT_TIMEOUT) {
			Log.w(TAG, "Detected NULL metadata for an app, reconnecting")
			lastConnectTime = System.currentTimeMillis()
			musicBrowser?.reconnect()
		}
	}

	private inner class Callback: MediaControllerCompat.Callback() {
		override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
			scheduleRedraw()
		}

		override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
			scheduleRedraw()
		}

		override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
			scheduleRedraw()
		}
	}
}