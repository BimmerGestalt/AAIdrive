package me.hufman.androidautoidrive.music

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomActionTest {
	fun caNamed(packageName: String, action: String): CustomAction {
		return CustomAction(packageName, action, action, null, null)
	}

	@Test
	fun unknown() {
		assertEquals("Unknown",
			CustomAction.formatCustomActionDisplay(caNamed("test", "Unknown")).name)
	}

	@Test
	fun rocketPlayerActions() {
		val caFormatted: (String) -> CustomAction = {
			CustomAction.formatCustomActionDisplay(caNamed("com.jrtstudio.AnotherMusicPlayer", it)) }
		assertEquals("Shuffle",
			caFormatted("Shuffle821435").name)
		assertEquals("Shuffle 3 songs",
			caFormatted("Shuffle 3 songs").name)
	}

	@Test
	fun spotifyActions() {
		val caFormatted: (String) -> CustomAction = {
			CustomAction.formatCustomActionDisplay(caNamed("com.spotify.music", it)) }
		assertEquals("Unknown",
			caFormatted("Unknown").name)
		assertEquals(L.MUSIC_TURN_SHUFFLE_ON,
			caFormatted("TURN_SHUFFLE_ON").name)
		assertEquals(L.MUSIC_TURN_SHUFFLE_OFF,
			caFormatted("TURN_REPEAT_SHUFFLE_OFF").name)
		assertEquals(L.MUSIC_TURN_SHUFFLE_OFF,
			caFormatted("TURN_SHUFFLE_OFF").name)
		assertEquals(L.MUSIC_SPOTIFY_REMOVE_FROM_COLLECTION,
			caFormatted("REMOVE_FROM_COLLECTION").name)
		assertEquals(L.MUSIC_SPOTIFY_START_RADIO,
			caFormatted("START_RADIO").name)
		assertEquals(L.MUSIC_TURN_REPEAT_ALL_ON,
			caFormatted("TURN_REPEAT_ALL_ON").name)
		assertEquals(L.MUSIC_TURN_REPEAT_ONE_ON,
			caFormatted("TURN_REPEAT_ONE_ON").name)
		assertEquals(L.MUSIC_TURN_REPEAT_OFF,
			caFormatted("TURN_REPEAT_ONE_OFF").name)
		assertEquals(L.MUSIC_SPOTIFY_ADD_TO_COLLECTION,
			caFormatted("ADD_TO_COLLECTION").name)
		assertEquals(L.MUSIC_SPOTIFY_THUMB_UP,
			caFormatted("THUMB_UP").name)
		assertEquals(L.MUSIC_SPOTIFY_THUMBS_UP_SELECTED,
			caFormatted("THUMBS_UP_SELECTED").name)
		assertEquals(L.MUSIC_SPOTIFY_THUMB_DOWN,
			caFormatted("THUMB_DOWN").name)
		assertEquals(L.MUSIC_SPOTIFY_THUMBS_DOWN_SELECTED,
			caFormatted("THUMBS_DOWN_SELECTED").name)
		assertEquals(L.MUSIC_ACTION_SEEK_BACK_15,
			caFormatted("SEEK_15_SECONDS_BACK").name)
		assertEquals(L.MUSIC_ACTION_SEEK_FORWARD_15,
			caFormatted("SEEK_15_SECONDS_FORWARD").name)
	}
}