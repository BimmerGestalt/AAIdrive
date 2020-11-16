package me.hufman.androidautoidrive.music

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettings
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class AppModeTest {
	@Test
	fun testMusicAppManual() {
		// Allow the user to force enable the context
		val settings = mock<MutableAppSettings> {
			on { get(AppSettings.KEYS.AUDIO_FORCE_CONTEXT) } doReturn "false"
			on { get(AppSettings.KEYS.AUDIO_SUPPORTS_USB) } doReturn "false"
		}
		val mode = MusicAppMode(emptyMap(), settings, null, null, null)
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
		val mode = MusicAppMode(emptyMap(), settings, null, null, null)
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
		val mode = MusicAppMode(emptyMap(), settings, null, null, null)
		assertTrue(mode.shouldRequestAudioContext())
	}

	@Test
	fun testId5() {
		IDriveConnectionListener.setConnection("", "127.0.0.1", 4007)   // BT connection
		val id6Capabilities = mapOf("hmi.type" to "ID6")
		val settings = mock<MutableAppSettings>()
		// spotify not installed
		run {
			val noSpotifyMode = MusicAppMode(id6Capabilities, settings, null, null, null)
			assertFalse(noSpotifyMode.shouldId5Playback())
		}
		// old spotify installed
		run {
			val oldSpotifyMode = MusicAppMode(id6Capabilities, settings, null, null, "8.4.98.892")
			assertFalse(oldSpotifyMode.shouldId5Playback())
		}
		// new spotify installed
		run {
			val newSpotifyMode = MusicAppMode(id6Capabilities, settings, null, null, "8.5.68.904")
			assertTrue(newSpotifyMode.shouldId5Playback())
		}
		// newer spotify installed
		run {
			val newerSpotifyMode = MusicAppMode(id6Capabilities, settings, null, null, "8.6.20")
			assertTrue(newerSpotifyMode.shouldId5Playback())
		}

		// force spotify layout
		whenever(settings[AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT]) doReturn "true"
		run {
			val forcedSpotifyMode = MusicAppMode(id6Capabilities, settings, null, null, null)
			assertTrue(forcedSpotifyMode.shouldId5Playback())
		}

		IDriveConnectionListener.reset()
	}

	@Test
	fun testId5Radio() {
		IDriveConnectionListener.setConnection("", "127.0.0.1", 4007)   // BT connection
		val id6Capabilities = mapOf("hmi.type" to "ID6")
		val settings = mock<MutableAppSettings>()

		// no radio app
		run {
			val noRadioMode = MusicAppMode(id6Capabilities, settings, null, null, null)
			assertEquals(null, noRadioMode.getRadioAppName())
		}
		// iHeartRadio
		run {
			val ihrRadioMode = MusicAppMode(id6Capabilities, settings, "yes", null, null)
			assertEquals("iHeartRadio", ihrRadioMode.getRadioAppName())
		}
		// Pandora
		run {
			val pandoraRadioMode = MusicAppMode(id6Capabilities, settings, null, "yes", null)
			assertEquals("Pandora", pandoraRadioMode.getRadioAppName())
		}
		// Both
		run {
			val bothRadioMode = MusicAppMode(id6Capabilities, settings, "yes", "yes", null)
			assertEquals(null, bothRadioMode.getRadioAppName())
		}
		IDriveConnectionListener.reset()

		// disabled in usb mode
		IDriveConnectionListener.setConnection("", "127.0.0.1", 4004)   // USB connection
		run {
			val ihrRadioMode = MusicAppMode(id6Capabilities, settings, "yes", null, null)
			assertEquals(null, ihrRadioMode.getRadioAppName())
		}

		IDriveConnectionListener.reset()
	}

	@After
	fun tearDown() {
		IDriveConnectionListener.reset()
	}
}