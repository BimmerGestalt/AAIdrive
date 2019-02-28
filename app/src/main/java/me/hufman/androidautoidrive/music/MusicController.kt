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
	private val TAG = "MusicController"
	var currentApp: MusicBrowser? = null
	var controller: MediaControllerCompat? = null
	private val controllerCallback = Callback()
	var listener: Runnable? = null
	var desiredPlayback = false  // if we should start playback as soon as connected

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

	fun playSong(song: MusicMetadata) = rpcSafe {
		val mediaId = song.mediaId ?: return
		controller?.transportControls?.playFromMediaId(mediaId, song.extras)
	}

	fun playQueue(song: MusicMetadata) = rpcSafe {
		if (song.queueId != null) {
			controller?.transportControls?.skipToQueueItem(song.queueId)
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

	private inner class Callback: MediaControllerCompat.Callback() {
		override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
			listener?.run()
		}

		override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
			listener?.run()
		}
	}
}