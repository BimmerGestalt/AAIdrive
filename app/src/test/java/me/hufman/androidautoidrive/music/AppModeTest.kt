package me.hufman.androidautoidrive.music

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MockAppSettings
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.idriveconnectionkit.android.IDriveConnectionStatus
import org.junit.Assert.*
import org.junit.Test

class AppModeTest {
	val usbConnection = mock<IDriveConnectionStatus> {
		on {port} doReturn MusicAppMode.TRANSPORT_PORTS.USB.toPort()
	}
	val btConnection = mock<IDriveConnectionStatus> {
		on {port} doReturn MusicAppMode.TRANSPORT_PORTS.BT.toPort()
	}
	val id4Capabilities = mapOf("hmi.type" to "ID4")
	val id6Capabilities = mapOf("hmi.type" to "ID6")

	@Test
	fun testMusicAppManual() {
		// Allow the user to force enable the context
		val settings = MockAppSettings(AppSettings.KEYS.AUDIO_FORCE_CONTEXT to "false", AppSettings.KEYS.AUDIO_SUPPORTS_USB to "false")
		val mode = MusicAppMode(usbConnection, emptyMap(), settings, null, null, null)
		assertFalse(mode.shouldRequestAudioContext())

		settings[AppSettings.KEYS.AUDIO_FORCE_CONTEXT] = "true"
		assertTrue(mode.shouldRequestAudioContext())
	}

	@Test
	fun testUSBSupport() {
		// Test that the USB connection is handled properly
		val settings = MockAppSettings(AppSettings.KEYS.AUDIO_FORCE_CONTEXT to "false", AppSettings.KEYS.AUDIO_SUPPORTS_USB to "false")
		val mode = MusicAppMode(usbConnection, emptyMap(), settings, null, null, null)
		assertFalse(mode.shouldRequestAudioContext())

		// the phone is old enough to support it over USB
		settings[AppSettings.KEYS.AUDIO_SUPPORTS_USB] = "true"
		assertTrue(mode.shouldRequestAudioContext())

		// should work over BT too, even if the phone is old
		val btMode = MusicAppMode(btConnection, emptyMap(), settings, null, null, null)
		assertTrue(btMode.shouldRequestAudioContext())
	}

	@Test
	fun testBTSupport() {
		// Verify that the BT connection is handled properly
		val settings = MockAppSettings(AppSettings.KEYS.AUDIO_FORCE_CONTEXT to "false", AppSettings.KEYS.AUDIO_SUPPORTS_USB to "false")
		val mode = MusicAppMode(btConnection, emptyMap(), settings, null, null, null)
		assertTrue(mode.shouldRequestAudioContext())
	}

	@Test
	fun testId5() {
		val settings = MockAppSettings()
		// spotify not installed
		run {
			val noSpotifyMode = MusicAppMode(btConnection, id6Capabilities, settings, null, null, null)
			assertFalse(noSpotifyMode.shouldId5Playback())
		}
		// old spotify installed
		run {
			val oldSpotifyMode = MusicAppMode(btConnection, id6Capabilities, settings, null, null, "8.4.98.892")
			assertFalse(oldSpotifyMode.shouldId5Playback())
		}
		// new spotify installed
		run {
			val newSpotifyMode = MusicAppMode(btConnection, id6Capabilities, settings, null, null, "8.5.68.904")
			assertTrue(newSpotifyMode.shouldId5Playback())
		}
		// newer spotify installed
		run {
			val newerSpotifyMode = MusicAppMode(btConnection, id6Capabilities, settings, null, null, "8.6.20")
			assertTrue(newerSpotifyMode.shouldId5Playback())
		}

		// force spotify layout
		settings[AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT] = "true"
		run {
			val forcedSpotifyMode = MusicAppMode(btConnection, id6Capabilities, settings, null, null, null)
			assertTrue(forcedSpotifyMode.shouldId5Playback())
		}

		// can't do it in id4
		run {
			val forcedSpotifyMode = MusicAppMode(btConnection, id4Capabilities, settings, null, null, "8.6.20")
			assertFalse(forcedSpotifyMode.shouldId5Playback())
		}
	}

	@Test
	fun testId5Radio() {
		val settings = MockAppSettings()

		// no radio app
		run {
			val noRadioMode = MusicAppMode(btConnection, id6Capabilities, settings, null, null, null)
			assertEquals(null, noRadioMode.getRadioAppName())
		}
		// iHeartRadio
		run {
			val ihrRadioMode = MusicAppMode(btConnection, id6Capabilities, settings, "yes", null, null)
			assertEquals("iHeartRadio", ihrRadioMode.getRadioAppName())
		}
		// Pandora
		run {
			val pandoraRadioMode = MusicAppMode(btConnection, id6Capabilities, settings, null, "yes", null)
			assertEquals("Pandora", pandoraRadioMode.getRadioAppName())
		}
		// Both
		run {
			val bothRadioMode = MusicAppMode(btConnection, id6Capabilities, settings, "yes", "yes", null)
			assertEquals(null, bothRadioMode.getRadioAppName())
		}

		// disabled in usb mode
		run {
			val ihrRadioMode = MusicAppMode(usbConnection, id6Capabilities, settings, "yes", null, null)
			assertEquals(null, ihrRadioMode.getRadioAppName())
		}
	}

}