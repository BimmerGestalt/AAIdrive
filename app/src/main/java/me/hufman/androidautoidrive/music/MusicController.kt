package me.hufman.androidautoidrive.music

import android.content.Context
import android.os.DeadObjectException
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.*
import android.util.Log
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import me.hufman.androidautoidrive.AppSettings
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
	var currentApp: MusicBrowser? = null
	var controller: MediaControllerCompat? = null
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
					controller?.transportControls?.skipToPrevious()
					startedSeekingTime = 0  // cancel seeking, because skipToPrevious starts at the beginning of the previous song
				} else {
					controller?.transportControls?.seekTo(newPos)
				}
				handler.postDelayed(this, 300)
			}
		}
	}


	init {
		handler.post { scheduleRedrawProgress() }
	}

	/**
	 * don't crash for RPC disconnections
	 */
	private inline fun safeRpc(crossinline f: () -> Unit) {
		try {
			f()
		} catch (e: DeadObjectException) {
			// the controller disconnected
			controller = null
		}
	}
	/**
	 * Run on the handler's thread, and don't crash for RPC disconnections
	 */
	private inline fun asyncRpc(crossinline f: () -> Unit) {
		handler.post {
			try {
				f()
			} catch (e: DeadObjectException) {
				// the controller disconnected
				controller = null
			}
		}
	}
	/**
	 * If we are on the handler's thread, run the task
	 * else, schedule it for the handler to run
	 */
	private inline fun syncRpc(crossinline f: () -> Unit) {
		if (Looper.myLooper() == handler.looper) {
			try {
				f()
			} catch (e: DeadObjectException) {
				// the controller disconnected
				controller = null
			}
		} else {
			asyncRpc(f)
		}
	}
	/**
	 * Run this controller task on the handler's thread, and don't crash for RPC disconnections
	 */
	private inline fun asyncControl(crossinline f: (MediaControllerCompat?) -> Unit) {
		handler.post {
			try {
				f(controller)
			} catch (e: DeadObjectException) {
				// the controller disconnected
				controller = null
			}
		}
	}

	fun connectApp(app: MusicAppInfo) = asyncRpc {
		if (currentApp?.musicAppInfo == app) {
			play()
		} else {
			Log.i(TAG, "Switching current app connection from ${currentApp?.musicAppInfo} to $app")
			lastConnectTime = System.currentTimeMillis()
			disconnectApp()
			currentApp = MusicBrowser(context, handler, app)
			currentApp?.listener = Runnable {
				Log.d(TAG, "Notified of new music connection")
				controller = currentApp?.mediaController
				controller?.registerCallback(controllerCallback, handler)
				if (desiredPlayback) {
					Log.i(TAG, "Resuming playback on new music connection")
					play()
				}
				scheduleRedraw()
				saveDesiredApp(app)
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

	fun disconnectApp() {
		// trigger a pause of the current connected app
		disconnectAppAsync()

		// then clear out the saved controller object, to defer future play() calls
		controller = null
		currentApp?.disconnect()
		currentApp = null
	}
	fun disconnectAppAsync() = safeRpc { // all calls are already in the handler thread, don't go async
		if (controller != null) {
			controller?.unregisterCallback(controllerCallback)
			controller?.transportControls?.pause()
		}
	}

	/* Controls */
	fun play() = asyncControl {
		desiredPlayback = true
		if (controller == null) {
			Log.w(TAG, "Play request but no active music app connection")
		} else {
			if (controller?.playbackState?.state != STATE_PLAYING) {
				controller?.transportControls?.play()
			}
		}
	}
	fun pause() = asyncControl {
		desiredPlayback = false
		if (controller?.playbackState?.state != STATE_PAUSED) {
			controller?.transportControls?.pause()
		}
	}
	fun skipToPrevious() = asyncControl { controller ->
		controller?.transportControls?.skipToPrevious()
	}
	fun skipToNext() = asyncControl { controller ->
		controller?.transportControls?.skipToNext()
	}
	fun startRewind() = asyncControl { controller ->
		if (isSupportedAction(MusicAction.REWIND)) {
			controller?.transportControls?.rewind()
		} else {
			startedSeekingTime = System.currentTimeMillis()
			seekingDirectionForward = false
			seekingRunnable.run()
		}
	}
	fun startFastForward() = asyncControl { controller ->
		if (isSupportedAction(MusicAction.FAST_FORWARD)) {
			controller?.transportControls?.fastForward()
		} else {
			startedSeekingTime = System.currentTimeMillis()
			seekingDirectionForward = true
			seekingRunnable.run()
		}
	}
	fun stopSeeking() = asyncControl { controller ->
		startedSeekingTime = 0
		play()
	}

	fun playSong(song: MusicMetadata) = asyncControl { controller ->
		if (song.mediaId != null) {
			controller?.transportControls?.playFromMediaId(song.mediaId, song.extras)
		}
	}

	fun playQueue(song: MusicMetadata) = asyncControl { controller ->
		if (song.queueId != null) {
			controller?.transportControls?.skipToQueueItem(song.queueId)
		}
	}

	fun customAction(action: CustomAction) = asyncControl { controller ->
		if (action.packageName == controller?.packageName) {
			controller.transportControls?.sendCustomAction(action.action, action.extras)
		}
	}

	fun browseAsync(directory: MusicMetadata?): Deferred<List<MusicMetadata>> {
		val app = currentApp
		return GlobalScope.async {
			app?.browse(directory?.mediaId)?.map {
				MusicMetadata.fromMediaItem(it)
			} ?: LinkedList()
		}
	}

	fun searchAsync(query: String): Deferred<List<MusicMetadata>> {
		val app = currentApp
		return GlobalScope.async {
			app?.search(query)?.map {
				MusicMetadata.fromMediaItem(it)
			} ?: LinkedList()
		}
	}

	/* Current state */
	/** Gets the current queue */
	fun getQueue(): List<MusicMetadata>? {
		val queue = try {
			controller?.queue
		} catch (e: DeadObjectException) { null }
		return queue?.map { MusicMetadata.fromQueueItem(it) }
	}
	/** Gets the current song's title and other metadata */
	fun getMetadata(): MusicMetadata? {
		try {
			val mediaMetadata = controller?.metadata ?: return null
			val playbackState = controller?.playbackState
			return MusicMetadata.fromMediaMetadata(mediaMetadata, playbackState)
		} catch (e: DeadObjectException) { return null }
	}
	/** Gets the song's playback position */
	fun getPlaybackPosition(): PlaybackPosition {
		val playbackState = try {
			controller?.playbackState
		} catch (e: DeadObjectException) { null }
		return if (playbackState == null) {
			PlaybackPosition(true, 0, 0, 0)
		} else {
			val metadata = getMetadata()
			PlaybackPosition(playbackState.state == STATE_PAUSED ||
					playbackState.state == STATE_CONNECTING ||
					playbackState.state == STATE_BUFFERING,
					playbackState.lastPositionUpdateTime, playbackState.position, metadata?.duration ?: -1)
		}
	}
	fun getCustomActions(): List<CustomAction> {
		val playbackState = try {
			controller?.playbackState
		} catch (e: DeadObjectException) { null }
		return playbackState?.customActions?.map {
			CustomAction.fromFromCustomAction(context, currentApp?.musicAppInfo?.packageName ?: "", it)
		} ?: LinkedList()
	}
	fun isSupportedAction(action: MusicAction): Boolean {
		return try {
			(controller?.playbackState?.actions ?: 0) and action.flag > 0
		} catch (e: DeadObjectException) {
			false
		}
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
	fun assertPlayingMetadata() {
		val controller = controller
		val metadata = controller?.metadata
		if (controller != null && metadata == null && System.currentTimeMillis() > lastConnectTime + RECONNECT_TIMEOUT) {
			Log.w(TAG, "Detected NULL metadata for an app, reconnecting")
			lastConnectTime = System.currentTimeMillis()
			currentApp?.reconnect()
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