package me.hufman.androidautoidrive.music.spotify

import android.util.Log
import com.spotify.android.appremote.api.PlayerApi
import com.spotify.protocol.types.PlayerContext
import com.spotify.protocol.types.PlayerState
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController

class SpotifySkipQueueController(
) {
	companion object {
		const val STABILITY_WAIT = 2000L
	}

	private var readyToSkip: Boolean = false

	private var queueUri: String? = null
	var isSkipQueueType = false
	var isInvertedSkip = false

	/** The previous playback position */
	private var previousPosition = -1L
	/** The current item that is playing */
	private var currentItemUri: String? = null
	/** The queue item that we want to skip towards */
	private var desiredItemUri: String? = null
	/** What the current shuffle state is */
	private var currentShuffleState: Boolean = false
	/** After we achieve the proper item, set the shuffle to this state */
	private var desiredShuffleState: Boolean = false

	private var lastSkipTime: Long = System.currentTimeMillis()
	private val timeSinceLastSkip: Long
		get() = System.currentTimeMillis() - lastSkipTime

	/** Remember the previous index */
	private var previousIndex: Int? = null
	/** Remember which direction we skipped before */
	private var previousSkipDirection: Boolean? = null

	/** Begin skipping to a specific queue position */
	fun startSkipping(state: PlayerState, desiredItemUri: String?) {
		currentItemUri = state.track?.uri
		this.desiredItemUri = desiredItemUri
		this.desiredShuffleState = state.playbackOptions.isShuffling
		this.previousPosition = state.playbackPosition

		readyToSkip = isSkipQueueType
		previousIndex = null
		previousSkipDirection = null
	}

	/** Update the player state */
	fun updateState(state: PlayerState) {
		val seekBack = previousPosition > state.playbackPosition
		val changedSong = currentItemUri != state.track.uri
		if (seekBack || changedSong) {
			readyToSkip = true
		}
		previousPosition = state.playbackPosition
		currentItemUri = state.track?.uri

		currentShuffleState = state.playbackOptions.isShuffling

		if (timeSinceLastSkip > STABILITY_WAIT) {
			// clear the state tracking
			this.desiredItemUri = null
		}
	}

	/** Update any playlist-specific state */
	fun updateContext(playerContext: PlayerContext) {
		if (queueUri != playerContext.uri) {
			queueUri = playerContext.uri
			val isLikedSongsPlaylist = playerContext.type == "your_library" || playerContext.type == "your_library_tracks"
			isSkipQueueType = isLikedSongsPlaylist

			isInvertedSkip = false
		}
	}

	/**
	 * Returns which way to skip
	 * True -> Next
	 * False -> Previous
	 */
	fun decideSkipDirection(queueItems: List<MusicMetadata>): Boolean {
		var currentIndex = -1
		queueItems.forEachIndexed { index, it ->
			if (it.mediaId == currentItemUri) {
				currentIndex = index
			}
		}
		var desiredIndex = -1
		queueItems.forEachIndexed { index, it ->
			if (it.mediaId == desiredItemUri) {
				desiredIndex = index
			}
		}

		// assuming index 0 is the start, and Next goes up to index 1
		val skipDirection = currentIndex < desiredIndex

		// check if we skipped before and it went the wrong way
		val previousIndex = previousIndex
		if (previousIndex != null && previousIndex != currentIndex) {
			val previousActualDirection = previousIndex < currentIndex
			isInvertedSkip = previousSkipDirection != previousActualDirection
		}

		// update the skipping to match
		val correctSkipDirection = if (isInvertedSkip) {
			!skipDirection
		} else {
			skipDirection
		}

		Log.i(SpotifyAppController.TAG, "Setting previousSkipDirection to $correctSkipDirection")
		this.previousIndex = currentIndex
		this.previousSkipDirection = correctSkipDirection

		return correctSkipDirection
	}

	/** Iteratively skip back/next towards the desired queue item */
	fun skipTowards(controller: PlayerApi, queueItems: List<MusicMetadata>) {
		val currentItemId = this.currentItemUri ?: return
		val desiredItemUri = this.desiredItemUri ?: return
		Log.i(SpotifyAppController.TAG, "Comparing $currentItemId to ${desiredItemUri}")

		// we reached it
		if (currentItemId == desiredItemUri) {
			Log.i(SpotifyAppController.TAG, "Reached destination ${currentItemId}")
			// set the final shuffle state
			if (desiredShuffleState != currentShuffleState) {
				controller.toggleShuffle()
			}
			return
		}

		// Did Spotify seek to the beginning of a track?
		if (!readyToSkip) {
			// no change, wait for the previous skip result to finish
			return
		}

		// make sure we aren't shuffling
		if (currentShuffleState) {
			controller.toggleShuffle()
		}

		// determine which direction to skip
		lastSkipTime = System.currentTimeMillis()
		val previousSkipDirection = previousSkipDirection
		val skipDirection = decideSkipDirection(queueItems)
		Log.i(SpotifyAppController.TAG, "Comparing previousSkipDirection $previousSkipDirection to new $skipDirection")
		if (previousSkipDirection != null && previousSkipDirection != skipDirection) {
			Log.w(SpotifyAppController.TAG, "Failed to skip to desired queue song, skipped too far!")
			this.desiredItemUri = null
			return
		}

		// do the skip
		readyToSkip = false
		if (skipDirection) {
			Log.i(SpotifyAppController.TAG, "Skipping next")
			controller.skipNext()
		} else {
			Log.i(SpotifyAppController.TAG, "Skipping back")
			controller.skipPrevious()
		}
	}
}