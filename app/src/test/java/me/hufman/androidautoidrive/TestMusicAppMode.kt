package me.hufman.androidautoidrive

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class TestMusicAppMode {
	@Test
	fun testMusicAppManual() {
		// Allow the user to force enable the context
		val settings = mock<MutableAppSettings> {
			on { get(AppSettings.KEYS.AUDIO_FORCE_CONTEXT) } doReturn "false"
			on { get(AppSettings.KEYS.AUDIO_SUPPORTS_USB) } doReturn "false"
		}
		val mode = MusicAppMode(emptyMap(), settings, null)
		assertFalse(mode.shouldRequestAudioContext())

		whenever(settings[AppSettings.KEYS.AUDIO_FORCE_CONTEXT]) doReturn "true"
		assertTrue(mode.shouldRequestAudioContext())
	}

	@Test
	fun testUSBSupport() {
		// Test that the USB connection is handled properl
		IDriveConnectionListener.setConnection("", "127.0.0.1", 4004)   // USB connection
		val settings = mock<MutableAppSettings> {
			on { get(AppSettings.KEYS.AUDIO_FORCE_CONTEXT) } doReturn "false"
			on { get(AppSettings.KEYS.AUDIO_SUPPORTS_USB) } doReturn "false"
		}
		val mode = MusicAppMode(emptyMap(), settings, null)
		assertFalse(mode.shouldRequestAudioContext())

		// the phone is old enough to support it
		whenever(settings[AppSettings.KEYS.AUDIO_SUPPORTS_USB]) doReturn "true"
		assertTrue(mode.shouldRequestAudioContext())

		// should work over BT too, even if the phone is old
		IDriveConnectionListener.setConnection("", "127.0.0.1", 4007)   // BT connection
		assertTrue(mode.shouldRequestAudioContext())
	}

	@Test
	fun testBTSupport() {
		// Verify that the BT connection is handled properly
		IDriveConnectionListener.setConnection("", "127.0.0.1", 4007)   // BT connection
		val settings = mock<MutableAppSettings> {
			on { get(AppSettings.KEYS.AUDIO_FORCE_CONTEXT) } doReturn "false"
			on { get(AppSettings.KEYS.AUDIO_SUPPORTS_USB) } doReturn "false"
		}
		val mode = MusicAppMode(emptyMap(), settings, null)
		assertTrue(mode.shouldRequestAudioContext())
	}

	@Test
	fun testId5() {
		IDriveConnectionListener.setConnection("", "127.0.0.1", 4007)   // BT connection
		val id6Capabilities = mapOf("hmi.type" to "ID6")
		val settings = mock<MutableAppSettings>()
		// spotify not installed
		val noSpotifyMode = MusicAppMode(id6Capabilities, settings, null)
		assertFalse(noSpotifyMode.shouldId5Playback())
		// old spotify installed
		val oldSpotifyMode = MusicAppMode(id6Capabilities, settings, "8.4.98.892")
		assertFalse(oldSpotifyMode.shouldId5Playback())
		// new spotify installed
		val newSpotifyMode = MusicAppMode(id6Capabilities, settings, "8.5.68.904")
		assertTrue(newSpotifyMode.shouldId5Playback())
		// newer spotify installed
		val newerSpotifyMode = MusicAppMode(id6Capabilities, settings, "8.6.20")
		assertTrue(newerSpotifyMode.shouldId5Playback())

		// force spotify layout
		whenever(settings[AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT]) doReturn "true"
		val forcedSpotifyMode = MusicAppMode(id6Capabilities, settings, null)
		assertTrue(forcedSpotifyMode.shouldId5Playback())
	}

	@After
	fun tearDown() {
		IDriveConnectionListener.reset()
	}
}