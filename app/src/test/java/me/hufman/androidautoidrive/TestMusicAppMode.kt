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
		val mode = MusicAppMode(settings)
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
		val mode = MusicAppMode(settings)
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
		val mode = MusicAppMode(settings)
		assertTrue(mode.shouldRequestAudioContext())
	}

	@After
	fun tearDown() {
		IDriveConnectionListener.reset()
	}
}