package me.hufman.androidautoidrive.music

import android.content.Context
import android.os.DeadObjectException
import android.os.Handler
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.*
import android.util.Log
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
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
	}

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
				handler.postDelayed(this, 500)
			}
		}
	}


	init {
		handler.post { scheduleRedrawProgress() }
	}

	private inline fun rpcSafe(f: () -> Unit) {
		try {
			f()
		} catch (e: DeadObjectException) {
			// the controller disconnected
			controller = null
		}
	}
	fun connectApp(app: MusicAppInfo) = rpcSafe {
		if (currentApp?.musicAppInfo == app) {
			play()
			return
		}

		disconnectApp()
		currentApp = MusicBrowser(context, handler, app)
		currentApp?.listener = Runnable {
			controller = currentApp?.getController()
			controller?.registerCallback(controllerCallback, handler)
			if (desiredPlayback)
				play()
			listener?.run() // redraw the ui
		}
	}

	fun disconnectApp() = rpcSafe {
		if (controller != null) {
			controller?.unregisterCallback(controllerCallback)
			pause()
		}
		controller = null
		currentApp?.disconnect()
	}

	/* Controls */
	fun play() = rpcSafe {
		if (controller == null) {
			Log.w(TAG, "Play request but no active music app connection")
		}
		desiredPlayback = true
		try {
			if (controller?.playbackState?.state != STATE_PLAYING) {
				controller?.transportControls?.play()
			}
		} catch (e: DeadObjectException) {
			controller = null
		}
	}
	fun pause() = rpcSafe {
		desiredPlayback = false
		if (controller?.playbackState?.state != STATE_PAUSED) {
			controller?.transportControls?.pause()
		}
	}
	fun skipToPrevious() = rpcSafe {
		controller?.transportControls?.skipToPrevious()
	}
	fun skipToNext() = rpcSafe {
		controller?.transportControls?.skipToNext()
	}
	fun startRewind() = rpcSafe {
		if (isSupportedAction(MusicAction.REWIND)) {
			controller?.transportControls?.rewind()
		} else {
			startedSeekingTime = System.currentTimeMillis()
			seekingDirectionForward = false
			seekingRunnable.run()
		}
	}
	fun startFastForward() = rpcSafe {
		if (isSupportedAction(MusicAction.FAST_FORWARD)) {
			controller?.transportControls?.fastForward()
		} else {
			startedSeekingTime = System.currentTimeMillis()
			seekingDirectionForward = true
			seekingRunnable.run()
		}
	}
	fun stopSeeking() = rpcSafe {
		startedSeekingTime = 0
		play()
	}

	fun playSong(song: MusicMetadata) = rpcSafe {
		val mediaId = song.mediaId ?: return
		controller?.transportControls?.playFromMediaId(mediaId, song.extras)
	}

	fun playQueue(song: MusicMetadata) = rpcSafe {
		if (song.queueId != null) {
			controller?.transportControls?.skipToQueueItem(song.queueId)
		}
	}

	fun customAction(action: CustomAction) = rpcSafe {
		if (action.packageName == controller?.packageName) {
			controller?.transportControls?.sendCustomAction(action.action, action.extras)
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
		if (controller == null) {
			Log.w(TAG, "Can't load metadata from null music app connection")
		}
		try {
			val mediaMetadata = controller?.metadata ?: return null
			val playbackState = controller?.playbackState
			return MusicMetadata.fromMediaMetadata(mediaMetadata, playbackState)
		} catch (e: DeadObjectException) { return null }
	}
	/** Gets the song's playback position */
	fun getPlaybackPosition(): PlaybackPosition {
		if (controller == null) {
			Log.w(TAG, "Can't load playback position from null music app connection")
		}
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
		return (controller?.playbackState?.actions ?: 0) and action.flag > 0
	}


	val redrawProgressTask = Runnable {
		redrawProgress()
	}
	fun redrawProgress() {
		handler.removeCallbacks(redrawProgressTask)

		listener?.run()
		scheduleRedrawProgress()
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

	private inner class Callback: MediaControllerCompat.Callback() {
		override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
			listener?.run()
		}

		override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
			listener?.run()
		}
	}
}