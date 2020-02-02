package me.hufman.androidautoidrive.music.controllers

import android.os.DeadObjectException
import kotlinx.coroutines.Deferred
import me.hufman.androidautoidrive.music.CustomAction
import me.hufman.androidautoidrive.music.MusicAction
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.music.PlaybackPosition

interface MusicAppController {
	@Throws(DeadObjectException::class)
	fun play()

	@Throws(DeadObjectException::class)
	fun pause()

	@Throws(DeadObjectException::class)
	fun skipToPrevious()

	@Throws(DeadObjectException::class)
	fun skipToNext()

	@Throws(DeadObjectException::class)
	fun seekTo(newPos: Long)

	@Throws(DeadObjectException::class)
	fun playSong(song: MusicMetadata)

	@Throws(DeadObjectException::class)
	fun playQueue(song: MusicMetadata)

	@Throws(DeadObjectException::class)
	fun playFromSearch(search: String)

	@Throws(DeadObjectException::class)
	fun customAction(action: CustomAction)

	/* Current state */
	@Throws(DeadObjectException::class)
	fun getQueue(): List<MusicMetadata>

	@Throws(DeadObjectException::class)
	fun getMetadata(): MusicMetadata?

	@Throws(DeadObjectException::class)
	fun getPlaybackPosition(): PlaybackPosition

	@Throws(DeadObjectException::class)
	fun isSupportedAction(action: MusicAction): Boolean

	@Throws(DeadObjectException::class)
	fun getCustomActions(): List<CustomAction>

	fun browseAsync(directory: MusicMetadata?): Deferred<List<MusicMetadata>>

	fun searchAsync(query: String): Deferred<List<MusicMetadata>>

	fun disconnect()
}