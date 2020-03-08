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
	var musicBrowser: MusicBrowser? = null
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
	private inline fun <R> withController(f: (MediaControllerCompat?) -> R): R {
		val musicSessionsController = musicSessions.mediaController
		val musicBrowserController = musicBrowser?.mediaController
		if (musicSessionsController != null) {
			try {
				val response = f(musicSessionsController)
				if (response != null) {
					return response
				}
			} catch (e: DeadObjectException) {
				// the controller disconnected
				musicSessions.mediaController = null
			}
		}
		if (musicBrowserController != null) {
			try {
				val response = f(musicBrowserController)
				if (response != null) {
					return response
				}
			} catch (e: DeadObjectException) {
				// the controller disconnected
				musicBrowser?.disconnect()
			}
		}
		return f(null)
	}
	/**
	 * Run this controller task on the handler's thread, and don't crash for RPC disconnections
	 * It will apply to the controller that is connected when the task fires
	 */
	private inline fun asyncControl(crossinline f: (MediaControllerCompat?) -> Unit) {
		handler.post {
			withController(f)
		}
	}

	fun connectApp(app: MusicAppInfo) = asyncRpc {
		val switchApp = musicBrowser?.musicAppInfo != app
		val needsReconnect = (app.connectable && musicBrowser?.mediaController == null) || musicSessions.mediaController == null
		if (switchApp || needsReconnect) {
			Log.i(TAG, "Switching current app connection from ${musicBrowser?.musicAppInfo} to $app")
			disconnectApp(pause = switchApp)
			musicSessions.connectApp(app)
			lastConnectTime = System.currentTimeMillis()
			musicBrowser = MusicBrowser(context, handler, app)
			musicBrowser?.listener = Runnable {
				Log.d(TAG, "Notified of new music browser connection")
				musicBrowser?.mediaController?.registerCallback(controllerCallback, handler)
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

	fun isConnected(): Boolean {
		return musicBrowser?.mediaController != null ||
				musicSessions.mediaController != null
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
	}

	/* Controls */
	fun play() = asyncControl { controller ->
		desiredPlayback = true
		if (controller == null) {
			Log.w(TAG, "Play request but no active music app connection")
		} else {
			if (controller.playbackState?.state != STATE_PLAYING) {
				controller.transportControls?.play()
			}
		}
	}
	fun playFromSearch(search: String) = asyncControl { controller ->
		controller?.transportControls?.playFromSearch(search, null)
	}
	fun pause() = asyncControl { controller ->
		desiredPlayback = false
		if (controller?.playbackState?.state != STATE_PAUSED) {
			controller?.transportControls?.pause()
		}
	}
	fun pauseSync() = withController { controller -> // all calls are already in the handler thread, don't go async
		if (controller != null) {
			controller.transportControls?.pause()
		}
	}
	fun skipToPrevious() = asyncControl { controller ->
		controller?.transportControls?.skipToPrevious()
	}
	fun skipToNext() = asyncControl { controller ->
		controller?.transportControls?.skipToNext()
	}
	fun startRewind() = asyncControl { controller ->
		startedSeekingTime = System.currentTimeMillis()
		seekingDirectionForward = false
		seekingRunnable.run()
	}
	fun startFastForward() = asyncControl { controller ->
		startedSeekingTime = System.currentTimeMillis()
		seekingDirectionForward = true
		seekingRunnable.run()
	}
	fun stopSeeking() = asyncControl { controller ->
		startedSeekingTime = 0
		play()
	}
	fun seekTo(newPos: Long) = asyncControl { controller ->
		controller?.transportControls?.seekTo(newPos)
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
		val app = musicBrowser
		return GlobalScope.async {
			app?.browse(directory?.mediaId)?.map {
				MusicMetadata.fromMediaItem(it)
			} ?: LinkedList()
		}
	}

	fun searchAsync(query: String): Deferred<List<MusicMetadata>> {
		val app = musicBrowser
		return GlobalScope.async {
			app?.search(query)?.map {
				MusicMetadata.fromMediaItem(it)
			} ?: LinkedList()
		}
	}

	/* Current state */
	/** Gets the current queue */
	fun getQueue(): List<MusicMetadata>? = withController { controller ->
		return controller?.queue?.map { MusicMetadata.fromQueueItem(it) }
	}
	/** Gets the current song's title and other metadata */
	fun getMetadata(): MusicMetadata? = withController { controller ->
		val mediaMetadata = controller?.metadata ?: return null
		val playbackState = controller.playbackState
		return MusicMetadata.fromMediaMetadata(mediaMetadata, playbackState)
	}
	/** Gets the song's playback position */
	fun getPlaybackPosition(): PlaybackPosition = withController { controller ->
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
	fun getCustomActions(): List<CustomAction> = withController { controller ->
		val playbackState = controller?.playbackState

		val customActions = playbackState?.customActions?.map {
			CustomAction.fromMediaCustomAction(context, musicBrowser?.musicAppInfo?.packageName ?: "", it)
		} ?: LinkedList()

		return customActions.map {formatCustomActionDisplay(it) }
	}

	private fun formatCustomActionDisplay(ca: CustomAction): CustomAction{
		if(ca.packageName == "com.spotify.music")
		{
			val niceName: String

			when(ca.action)
			{
				"TURN_SHUFFLE_ON" ->
					niceName = L.MUSIC_SPOTIFY_TURN_SHUFFLE_ON
				"TURN_REPEAT_SHUFFLE_OFF" ->
					niceName = L.MUSIC_SPOTIFY_TURN_SHUFFLE_OFF
				"TURN_SHUFFLE_OFF" ->
					niceName = L.MUSIC_SPOTIFY_TURN_SHUFFLE_OFF

				"REMOVE_FROM_COLLECTION" ->
					niceName = L.MUSIC_SPOTIFY_REMOVE_FROM_COLLECTION

				"START_RADIO" ->
					niceName = L.MUSIC_SPOTIFY_START_RADIO

				"TURN_REPEAT_ALL_ON" ->
					niceName = L.MUSIC_SPOTIFY_TURN_REPEAT_ALL_ON
				"TURN_REPEAT_ONE_ON" ->
					niceName = L.MUSIC_SPOTIFY_TURN_REPEAT_ONE_ON
				"TURN_REPEAT_ONE_OFF" ->
					niceName = L.MUSIC_SPOTIFY_TURN_REPEAT_ONE_OFF
				"ADD_TO_COLLECTION" ->
					niceName = L.MUSIC_SPOTIFY_ADD_TO_COLLECTION
				else ->
					niceName = ca.name
			}

			return CustomAction(ca.packageName, ca.action, niceName, ca.icon, ca.extras);
		}

		if (ca.packageName == "com.jrtstudio.AnotherMusicPlayer") {
			val rocketPlayerActionPattern = Regex("([A-Za-z]+)[0-9]+")
			val match = rocketPlayerActionPattern.matchEntire(ca.name)
			if (match != null) {
				return CustomAction(ca.packageName, ca.action, match.groupValues[1], ca.icon, ca.extras)
			}
		}

		return ca
	}

	fun isSupportedAction(action: MusicAction): Boolean = withController { controller ->
		return (controller?.playbackState?.actions ?: 0) and action.flag > 0
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
		val metadata = controller?.metadata
		val packageName = controller?.packageName
		if (controller != null && packageName == "com.google.android.youtube" && metadata == null && System.currentTimeMillis() > lastConnectTime + RECONNECT_TIMEOUT) {
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