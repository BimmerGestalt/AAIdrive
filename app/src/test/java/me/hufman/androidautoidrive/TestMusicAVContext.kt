package me.hufman.androidautoidrive

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingClient
import me.hufman.androidautoidrive.carapp.music.AVContextHandler
import me.hufman.androidautoidrive.carapp.music.MusicApp
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.carapp.music.MusicImageIDsMultimedia
import me.hufman.androidautoidrive.music.*
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationEtch
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationSynchronized
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import me.hufman.idriveconnectionkit.rhmi.RHMIState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class TestMusicAVContext {
	val securityAccess = mock<SecurityAccess> {
		on { signChallenge(any(), any() )} doReturn ByteArray(512)
	}
	val carAppResources = mock<CarAppResources> {
		on { getAppCertificate() } doReturn ByteArrayInputStream(ByteArray(0))
		on { getUiDescription() } doAnswer { this.javaClass.classLoader.getResourceAsStream("ui_description_multimedia_v2.xml") }
		on { getImagesDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
		on { getTextsDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
	}

	val phoneAppResources = mock<PhoneAppResources> {
		on { getAppName(any()) } doReturn "Test AppName"
		on { getAppIcon(any())} doReturn mock<Drawable>()
		on { getIconDrawable(any())} doReturn mock<Drawable>()
	}

	val graphicsHelpers = mock<GraphicsHelpers> {
		on { isDark(any()) } doReturn false
		on { compress(isA<Drawable>(), any(), any(), any(), any()) } doAnswer {"Drawable{${it.arguments[1]}x${it.arguments[2]}}".toByteArray()}
		on { compress(isA<Bitmap>(), any(), any(), any(), any()) } doAnswer {"Bitmap{${it.arguments[1]}x${it.arguments[2]}}".toByteArray()}
	}

	val musicAppDiscovery = mock<MusicAppDiscovery>()

	val musicController = mock<MusicController> {
		on { getMetadata() } doReturn MusicMetadata("testId", queueId=10,
				duration=180000L,
				icon=mock(), coverArt=mock(),
				artist="Artist", album="Album", title="Title")
		on { getPlaybackPosition() } doReturn PlaybackPosition(false, 0, 5000L, 180000L)
		on { isSupportedAction(any()) } doReturn true
		on { isSupportedAction(MusicAction.PLAY_FROM_SEARCH) } doReturn false
		on { loadDesiredApp() } doReturn ""
		on { musicSessions } doReturn mock<MusicSessions>()
		on { isConnected() } doReturn true
	}

	/** Test that a Spotify AM icon lines up where the Spotify RHMI icon goes */
	@Test
	fun testAmSpotifyWeight() {
		val musicAppMode = mock<MusicAppMode> {
			on { shouldId5Playback() } doReturn true
		}
		val avContext = AVContextHandler(RHMIApplicationSynchronized(mock<RHMIApplicationEtch>()), mock(), mock(), musicAppMode)
		val spotifyAm = avContext.getAMInfo(MusicAppInfo("Spotify", mock(), "com.spotify.music", null))
		assertEquals(500, spotifyAm[5])
	}

	/** Test that a music app turns into an AM Icon and then plays that app when clicked */
	@Test
	fun testAmClick() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val mode = mock<MusicAppMode> {
			on { shouldRequestAudioContext() } doReturn false
		}
		val app = MusicApp(securityAccess, carAppResources, MusicImageIDsMultimedia, phoneAppResources, graphicsHelpers, musicAppDiscovery, musicController, mode)
		val mockClient = IDriveConnection.mockRemotingClient as BMWRemotingClient

		// post a new application
		val musicAppInfo = MusicAppInfo("Test", mock(), "example.test", null)
		whenever(musicAppDiscovery.connectableApps) doAnswer {listOf(musicAppInfo)}
		val musicAppListener = argumentCaptor<Runnable>()
		verify(musicAppDiscovery).listener = musicAppListener.capture()
		musicAppListener.lastValue.run()

		// it should create an AM icon
		assertEquals(1, mockServer.amApps.size)
		val ident = mockServer.amApps[0]
		assertEquals("androidautoidrive.example.test", mockServer.amApps[0])

		// user clicks the icon
		mockClient.am_onAppEvent(1, "1", ident, BMWRemoting.AMEvent.AM_APP_START)

		// and it should start playing that app
		verify(musicController).connectAppManually(musicAppInfo)

		// it should also redraw the am icon
		assertEquals(2, mockServer.amApps.size)
		assertEquals("androidautoidrive.example.test", mockServer.amApps[1])
	}

	/** Test the AV context functionality, like automatic pausing when losing it */
	@Test
	fun testAvContext() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val mode = mock<MusicAppMode> {
			on { shouldRequestAudioContext() } doReturn true
		}
		val app = MusicApp(securityAccess, carAppResources, MusicImageIDsMultimedia, phoneAppResources, graphicsHelpers, musicAppDiscovery, musicController, mode)
		val mockClient = IDriveConnection.mockRemotingClient as BMWRemotingClient

		// post a new application
		val musicAppInfo = MusicAppInfo("Test", mock(), "example.test", null)
		whenever(musicAppDiscovery.connectableApps) doAnswer {listOf(musicAppInfo)}
		val musicAppListener = argumentCaptor<Runnable>()
		verify(musicAppDiscovery).listener = musicAppListener.capture()
		musicAppListener.lastValue.run()

		// without an InstanceID, won't try to create an AvContext
		assertEquals(0, mockServer.avConnections.size)
		// now set the instanceId
		IDriveConnectionListener.setConnection("", "localhost", 4008, 9)
		musicAppListener.lastValue.run()
		// the context was created
		assertEquals(1, mockServer.avConnections.size)
		IDriveConnectionListener.reset()
		IDriveConnectionListener.setConnection("", "localhost", 4008, null)
		IDriveConnectionListener.reset()

		// user clicks the icon
		mockClient.am_onAppEvent(1, "1", mockServer.amApps[0], BMWRemoting.AMEvent.AM_APP_START)
		verify(musicController).connectAppManually(musicAppInfo)

		// test that the av context was created
		assertEquals(1, mockServer.avConnections.size)
		// the av context is requested
		assertEquals(0, mockServer.avCurrentContext)
		// car declares av context
		mockClient.av_connectionGranted(0, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT)

		// car declares playback state
		mockClient.av_requestPlayerState(0, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT, BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PLAY)
		// music controller is playing
		verify(musicController).play()

		// test the other playback states
		reset(musicController)
		mockClient.av_requestPlayerState(0, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT, BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PAUSE)
		verify(musicController).pause()
		reset(musicController)
		mockClient.av_requestPlayerState(0, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT, BMWRemoting.AVPlayerState.AV_PLAYERSTATE_STOP)
		verify(musicController).pause()
		reset(musicController)

		// car tells us to pause
		mockClient.av_connectionDeactivated(0, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT)
		// music controller is paused
		verify(musicController).pause()
	}

	/** Test the hardware seek buttons */
	@Test
	fun testAvActions() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val mode = mock<MusicAppMode> {
			on { shouldRequestAudioContext() } doReturn false
		}
		val app = MusicApp(securityAccess, carAppResources, MusicImageIDsMultimedia, phoneAppResources, graphicsHelpers, musicAppDiscovery, musicController, mode)
		val mockClient = IDriveConnection.mockRemotingClient as BMWRemotingClient

		// now click AV buttons
		mockClient.av_multimediaButtonEvent(1, BMWRemoting.AVButtonEvent.AV_EVENT_SKIP_UP)
		verify(musicController).skipToNext()
		mockClient.av_multimediaButtonEvent(1, BMWRemoting.AVButtonEvent.AV_EVENT_SKIP_DOWN)
		verify(musicController).skipToPrevious()

		// long press AV buttons
		mockClient.av_multimediaButtonEvent(1, BMWRemoting.AVButtonEvent.AV_EVENT_SKIP_LONG_UP)
		verify(musicController).startFastForward()
		mockClient.av_multimediaButtonEvent(1, BMWRemoting.AVButtonEvent.AV_EVENT_SKIP_LONG_STOP)
		verify(musicController).stopSeeking()

		reset(musicController)
		mockClient.av_multimediaButtonEvent(1, BMWRemoting.AVButtonEvent.AV_EVENT_SKIP_LONG_DOWN)
		verify(musicController).startRewind()
		mockClient.av_multimediaButtonEvent(1, BMWRemoting.AVButtonEvent.AV_EVENT_SKIP_LONG_STOP)
		verify(musicController).stopSeeking()
	}

	/** Test behavior around resuming playback on a fresh start */
	@Test
	fun testAvReconnect() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val mode = mock<MusicAppMode> {
			on { shouldRequestAudioContext() } doReturn false
		}
		val app = MusicApp(securityAccess, carAppResources, MusicImageIDsMultimedia, phoneAppResources, graphicsHelpers, musicAppDiscovery, musicController, mode)
		val mockClient = IDriveConnection.mockRemotingClient as BMWRemotingClient

		val musicAppInfo = MusicAppInfo("Test", mock(), "example.test", null)
		whenever(musicAppDiscovery.connectableApps) doAnswer {listOf(musicAppInfo)}
		app.avContext.updateApps(listOf(musicAppInfo))

		// the previously-remembered app
		whenever(musicController.loadDesiredApp()) doReturn musicAppInfo.packageName
		// car wants us to start playing
		mockClient.av_connectionGranted(0, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT)
		// and so it should have resumed
		verify(musicController).connectAppAutomatically(musicAppInfo)

		// claim that an app is currently playing
		whenever(musicController.musicSessions.getPlayingApp()) doReturn musicAppInfo

		// car wants us to start playing
		mockClient.av_connectionGranted(0, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT)
	}
}