package me.hufman.androidautoidrive.music

import android.content.Context
import android.os.Handler
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.*
import android.util.Log

class MusicController(val context: Context, val handler: Handler) {
	private val TAG = "MusicController"
	var currentApp: MusicBrowser? = null
	var controller: MediaControllerCompat? = null
	private val controllerCallback = Callback()
	var listener: Runnable? = null
	var desiredPlayback = false  // if we should start playback as soon as connected

	fun connectApp(app: MusicAppInfo) {
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

	fun disconnectApp() {
		if (controller != null) {
			controller?.unregisterCallback(controllerCallback)
			pause()
		}
		controller = null
		currentApp?.disconnect()
	}

	/* Controls */
	fun play() {
		if (controller == null) {
			Log.w(TAG, "Play request but no active music app connection")
		}
		desiredPlayback = true
		if (controller?.playbackState?.state != STATE_PLAYING) {
			controller?.transportControls?.play()
		}
	}
	fun pause() {
		desiredPlayback = false
		if (controller?.playbackState?.state != STATE_PAUSED) {
			controller?.transportControls?.pause()
		}
	}
	fun skipToPrevious() {
		controller?.transportControls?.skipToPrevious()
	}
	fun skipToNext() {
		controller?.transportControls?.skipToNext()
	}

	/* Current state */
	/** Gets the current song's title and other metadata */
	fun getMetadata(): MusicMetadata? {
		if (controller == null) {
			Log.w(TAG, "Can't load metadata from null music app connection")
		}
		val mediaMetadata = controller?.metadata ?: return null
		return MusicMetadata.fromMediaMetadata(mediaMetadata)
	}
	/** Gets the song's playback position */
	fun getPlaybackPosition(): PlaybackPosition {
		if (controller == null) {
			Log.w(TAG, "Can't load playback position from null music app connection")
		}
		val playbackState = controller?.playbackState
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