package me.hufman.androidautoidrive.music.controllers

import me.hufman.androidautoidrive.Observable
import me.hufman.androidautoidrive.music.*

interface MusicAppController {
	interface Connector {
		fun connect(appInfo: MusicAppInfo): Observable<out MusicAppController>
	}

	fun play()

	fun pause()

	fun skipToPrevious()

	fun skipToNext()

	fun seekTo(newPos: Long)

	fun playSong(song: MusicMetadata)

	fun playQueue(song: MusicMetadata)

	fun playFromSearch(search: String)

	fun customAction(action: CustomAction)

	fun toggleShuffle()

	fun toggleRepeat()

	/* Current state */
	fun getQueue(): QueueMetadata?

	fun getMetadata(): MusicMetadata?

	fun getPlaybackPosition(): PlaybackPosition

	fun isSupportedAction(action: MusicAction): Boolean

	fun getCustomActions(): List<CustomAction>

	fun isShuffling(): Boolean

	fun getRepeatMode(): RepeatMode

	suspend fun browse(directory: MusicMetadata?): List<MusicMetadata>

	suspend fun search(query: String): List<MusicMetadata>?

	/**
	 * Subscribes to receive notice of new metadata or other status
	 */
	fun subscribe(callback: (MusicAppController) -> Unit)

	/**
	 * Returns whether the app is still connected
	 */
	fun isConnected(): Boolean
	/**
	 * Disconnects the app, and also clears out any subscribed callback
	 */
	fun disconnect()
}