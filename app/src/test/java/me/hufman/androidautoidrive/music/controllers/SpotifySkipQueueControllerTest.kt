package me.hufman.androidautoidrive.music.controllers

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.spotify.android.appremote.api.PlayerApi
import com.spotify.protocol.types.*
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.music.spotify.SpotifySkipQueueController
import org.junit.Assert.*
import org.junit.Test

fun PlayerState(track: Track, isPaused: Boolean = false,
		playbackSpeed: Float = 1.0f, playbackPosition: Long = 0,
		playbackOptions: PlayerOptions = mock(), playbackRestrictions: PlayerRestrictions = mock()): PlayerState {
	return com.spotify.protocol.types.PlayerState(track, isPaused, playbackSpeed, playbackPosition, playbackOptions, playbackRestrictions)
}

class SpotifySkipQueueControllerTest {
	val playerController = mock<PlayerApi>()
	val skipController = SpotifySkipQueueController()
	val queue = listOf(
			MusicMetadata("queueItem1"),
			MusicMetadata("queueItem2"),
			MusicMetadata("queueItem3"),
			MusicMetadata("queueItem4"),
			MusicMetadata("queueItem5")
	)
	val tracks = queue.map {
		Track(mock(), mock(), mock(), 300, "", it.mediaId, null, false, false)
	}

	@Test
	fun testLikedSongs() {
		skipController.updateContext(PlayerContext("collection", "", "", "your_library_tracks"))
		assertTrue(skipController.isSkipQueueType)

		skipController.updateContext(PlayerContext("artist", "", "", "artist_tracks"))
		assertFalse(skipController.isSkipQueueType)
	}

	@Test
	fun testSkipNext() {
		skipController.startSkipping(PlayerState(tracks[0], playbackPosition = 500), queue[3].mediaId)
		skipController.updateState(PlayerState(tracks[0]))
		skipController.skipTowards(playerController, queue)
		verify(playerController).skipNext()
		reset(playerController)
		// skip next
		skipController.updateState(PlayerState(tracks[1]))
		skipController.skipTowards(playerController, queue)
		verify(playerController).skipNext()
		reset(playerController)
		// skip next
		skipController.updateState(PlayerState(tracks[2]))
		skipController.skipTowards(playerController, queue)
		verify(playerController).skipNext()
		reset(playerController)
		// skip too far
		skipController.updateState(PlayerState(tracks[4]))
		skipController.skipTowards(playerController, queue)
		verifyNoMoreInteractions(playerController)
		reset(playerController)
	}

	@Test
	fun testSkipPrevious() {
		skipController.startSkipping(PlayerState(tracks[4], playbackPosition = 500), queue[2].mediaId)
		skipController.updateState(PlayerState(tracks[4]))
		assertFalse(skipController.decideSkipDirection(queue))
		skipController.skipTowards(playerController, queue)
		verify(playerController).skipPrevious()
		reset(playerController)
		// skip back
		skipController.updateState(PlayerState(tracks[3]))
		assertFalse(skipController.decideSkipDirection(queue))
		skipController.skipTowards(playerController, queue)
		verify(playerController).skipPrevious()
		reset(playerController)
	}

	@Test
	fun testSkipIncorrect() {
		skipController.startSkipping(PlayerState(tracks[1], playbackPosition = 500), queue[3].mediaId)
		skipController.updateState(PlayerState(tracks[1]))
		assertTrue(skipController.decideSkipDirection(queue))
		skipController.skipTowards(playerController, queue)
		verify(playerController).skipNext()
		reset(playerController)

		// skip next, but backwards
		skipController.updateState(PlayerState(tracks[0]))

		// Spotify is going backwards for this queue, so skip "backwards" towards #2
		assertFalse(skipController.decideSkipDirection(queue))
		skipController.skipTowards(playerController, queue)
		verify(playerController).skipPrevious()
		reset(playerController)
		skipController.updateState(PlayerState(tracks[1]))

		assertFalse(skipController.decideSkipDirection(queue))
		skipController.skipTowards(playerController, queue)
		verify(playerController).skipPrevious()
		reset(playerController)
		skipController.updateState(PlayerState(tracks[2]))

		assertFalse(skipController.decideSkipDirection(queue))
		skipController.skipTowards(playerController, queue)
		verify(playerController).skipPrevious()
		reset(playerController)
	}

	@Test
	fun testSkipIncorrectBackwards() {
		skipController.startSkipping(PlayerState(tracks[3], playbackPosition = 500), queue[1].mediaId)
		skipController.updateState(PlayerState(tracks[3]))
		assertFalse(skipController.decideSkipDirection(queue))
		skipController.skipTowards(playerController, queue)
		verify(playerController).skipPrevious()
		reset(playerController)

		// skip next, but backwards
		skipController.updateState(PlayerState(tracks[4]))

		// Spotify is going backwards for this queue, so skip "backwards" towards #2
		assertTrue(skipController.decideSkipDirection(queue))
		skipController.skipTowards(playerController, queue)
		verify(playerController).skipNext()
		reset(playerController)
		skipController.updateState(PlayerState(tracks[3]))

		assertTrue(skipController.decideSkipDirection(queue))
		skipController.skipTowards(playerController, queue)
		verify(playerController).skipNext()
		reset(playerController)
		skipController.updateState(PlayerState(tracks[2]))

		assertTrue(skipController.decideSkipDirection(queue))
		skipController.skipTowards(playerController, queue)
		verify(playerController).skipNext()
		reset(playerController)
	}
}