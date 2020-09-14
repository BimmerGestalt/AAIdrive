package me.hufman.androidautoidrive

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import me.hufman.androidautoidrive.carapp.music.AVContextHandler
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationEtch
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationSynchronized
import org.junit.Assert.assertEquals
import org.junit.Test

class TestMusicAVContext {
	@Test
	fun testAmSpotifyWeight() {
		val musicAppMode = mock<MusicAppMode> {
			on { shouldId5Playback() } doReturn true
		}
		val avContext = AVContextHandler(RHMIApplicationSynchronized(mock<RHMIApplicationEtch>()), mock(), mock(), musicAppMode)
		val spotifyAm = avContext.getAMInfo(MusicAppInfo("Spotify", mock(), "com.spotify.music", null))
		assertEquals(500, spotifyAm[5])
	}
}