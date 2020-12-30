package me.hufman.androidautoidrive.music

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingClient
import me.hufman.androidautoidrive.MockBMWRemotingServer
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.carapp.ConcreteAMAppInfo
import me.hufman.androidautoidrive.carapp.AMAppList
import me.hufman.androidautoidrive.carapp.AMCategory
import me.hufman.androidautoidrive.carapp.music.MusicApp
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.carapp.music.MusicImageIDsMultimedia
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionStatus
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class AVContextTest {
	val iDriveConnectionStatus = mock<IDriveConnectionStatus>()
	val securityAccess = mock<SecurityAccess> {
		on { signChallenge(any(), any() )} doReturn ByteArray(512)
	}
	val carAppResources = mock<CarAppResources> {
		on { getAppCertificate() } doReturn ByteArrayInputStream(ByteArray(0))
		on { getUiDescription() } doAnswer { this.javaClass.classLoader!!.getResourceAsStream("ui_description_multimedia_v2.xml") }
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
		on { getPlaybackPosition() } doReturn PlaybackPosition(false, false, 0, 5000L, 180000L)
		on { isSupportedAction(any()) } doReturn true
		on { isSupportedAction(MusicAction.PLAY_FROM_SEARCH) } doReturn false
		on { loadDesiredApp() } doReturn ""
		on { musicSessions } doReturn mock<MusicSessions>()
		on { isConnected() } doReturn true
	}

	/** Test that a Spotify AM icon lines up where the Spotify RHMI icon goes */
	@Test
	fun testAmSpotifyWeight() {
		val amHandler = AMAppList<MusicAppInfo>(mock(), mock(), "test")
		val spotifyAm = amHandler.getAMInfo(MusicAppInfo("Spotify", mock(), "com.spotify.music", null).apply {
			weightAdjustment = -173
		})
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
		MusicApp(iDriveConnectionStatus, securityAccess, carAppResources, MusicImageIDsMultimedia, phoneAppResources, graphicsHelpers, musicAppDiscovery, musicController, mode)
		val mockClient = IDriveConnection.mockRemotingClient as BMWRemotingClient

		// post a new application
		val musicAppInfo = MusicAppInfo("Test", mock(), "example.test", null)
		whenever(musicAppDiscovery.validApps) doAnswer {listOf(musicAppInfo)}
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
		// it should trigger focus to the PlaybackView
		assertEquals(mapOf(0.toByte() to MusicAppTest.IDs.PLAYBACK_STATE), mockServer.triggeredEvents[MusicAppTest.IDs.FOCUS_EVENT])

		// it should also redraw the am icon
		assertEquals(2, mockServer.amApps.size)
		assertEquals("androidautoidrive.example.test", mockServer.amApps[1])
	}

	/** Test that removing a music app resets the list */
	@Test
	fun testAmRebuild() {
		val mockServer = MockBMWRemotingServer()
		val amAppList = AMAppList<ConcreteAMAppInfo>(mockServer, mock(), "testIdent")
		val app1 = ConcreteAMAppInfo("test1", "test1", mock(), AMCategory.MULTIMEDIA)
		val app2 = ConcreteAMAppInfo("test2", "test2", mock(), AMCategory.MULTIMEDIA)
		amAppList.setApps(listOf(app1, app2))

		assertEquals(2, mockServer.amApps.size)
		assertEquals(1, mockServer.amHandles.size)

		// update with a new smaller list
		amAppList.setApps(listOf(app2))
		assertEquals(3, mockServer.amApps.size) // the single app has been added
		assertEquals(-1, mockServer.amHandles[0])   // the old amHandle was disposed
		assertEquals(2, mockServer.amHandles.size)  // a new amHandle was added

		// no changes if we have the same list
		amAppList.setApps(listOf(app2))
		assertEquals(3, mockServer.amApps.size)
		assertEquals(2, mockServer.amHandles.size)

		// make sure it clears if the same app exists but in a different section
		val app2Moved = ConcreteAMAppInfo("test2", "test2", mock(), AMCategory.RADIO)
		amAppList.setApps(listOf(app2Moved))
		assertEquals(4, mockServer.amApps.size)
		assertEquals(3, mockServer.amHandles.size)
	}

	/** Test that some apps are categorized as Radio apps */
	@Test
	fun testAmRadio() {
		val mockPackageManager = mock<PackageManager> {
			on { getApplicationInfo(any(), any()) } doReturn mock<ApplicationInfo>()
			on { getApplicationLabel(any()) } doReturn ""
			on { getApplicationIcon(isA<ApplicationInfo>()) } doReturn mock<Drawable>()
		}
		val mockContext = mock<Context> {
			on { packageManager } doReturn mockPackageManager
		}

		val MEDIA_APPS = mapOf(
			"Audible" to "com.audible.application",
			"Libro.fm" to "fm.libro.librofm",
			"Spotify" to "com.spotify.music"
		)
		val RADIO_APPS = mapOf(
			"DI.FM Radio" to "com.audioaddict.di",
			"RMC" to "it.froggy.android.rmc",
			"SiriusXM" to "com.sirius"
		)

		MEDIA_APPS.forEach { (name, packageName) ->
			whenever(mockPackageManager.getApplicationLabel(any())) doReturn name
			assertEquals("$name is Multimedia", AMCategory.MULTIMEDIA, MusicAppInfo.guessCategory(packageName, name))
			assertEquals("$name info is Multimedia", AMCategory.MULTIMEDIA, MusicAppInfo.getInstance(mockContext, packageName, null).category)
		}
		RADIO_APPS.forEach { (name, packageName) ->
			whenever(mockPackageManager.getApplicationLabel(any())) doReturn name
			assertEquals("$name is Radio", AMCategory.RADIO, MusicAppInfo.guessCategory(packageName, name))
			assertEquals("$name info is Radio", AMCategory.RADIO, MusicAppInfo.getInstance(mockContext, packageName, null).category)
		}

		whenever(mockPackageManager.getApplicationLabel(any())) doReturn "Spotify"
		val testCategory = MusicAppInfo.getInstance(mockContext, MEDIA_APPS.getValue("Spotify"), null)
		testCategory.forcedCategory = AMCategory.ADDRESSBOOK
		assertEquals(AMCategory.ADDRESSBOOK, testCategory.category)
	}

	/** Test the AV context functionality, like automatic pausing when losing it */
	@Test
	fun testAvContext() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val mode = mock<MusicAppMode> {
			on { shouldRequestAudioContext() } doReturn true
		}
		MusicApp(iDriveConnectionStatus, securityAccess, carAppResources, MusicImageIDsMultimedia, phoneAppResources, graphicsHelpers, musicAppDiscovery, musicController, mode)
		val mockClient = IDriveConnection.mockRemotingClient as BMWRemotingClient

		// post a new application
		val musicAppInfo = MusicAppInfo("Test", mock(), "example.test", null)
		whenever(musicAppDiscovery.validApps) doAnswer {listOf(musicAppInfo)}
		val musicAppListener = argumentCaptor<Runnable>()
		verify(musicAppDiscovery).listener = musicAppListener.capture()
		musicAppListener.lastValue.run()

		// without an InstanceID, won't try to create an AvContext
		assertEquals(0, mockServer.avConnections.size)
		// now set the instanceId
		whenever(iDriveConnectionStatus.instanceId) doReturn 9
		musicAppListener.lastValue.run()
		// the context was created
		assertEquals(1, mockServer.avConnections.size)

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

		// test that, when paused and current context, the user requesting playback through entrybutton does not start playback
		mockClient.am_onAppEvent(1, "1", mockServer.amApps[0], BMWRemoting.AMEvent.AM_APP_START)
		verify(musicController).connectAppManually(musicAppInfo)    // prepares but doesn't start playing
		verify(musicController, never()).play()

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
		MusicApp(iDriveConnectionStatus, securityAccess, carAppResources, MusicImageIDsMultimedia, phoneAppResources, graphicsHelpers, musicAppDiscovery, musicController, mode)
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
		MusicApp(iDriveConnectionStatus, securityAccess, carAppResources, MusicImageIDsMultimedia, phoneAppResources, graphicsHelpers, musicAppDiscovery, musicController, mode)
		val mockClient = IDriveConnection.mockRemotingClient as BMWRemotingClient

		val musicAppInfo = MusicAppInfo("Test", mock(), "example.test", null)
		whenever(musicAppDiscovery.validApps) doAnswer {listOf(musicAppInfo)}

		// set the previously-remembered app
		whenever(musicController.loadDesiredApp()) doReturn musicAppInfo.packageName

		// car wants us to start playing
		mockClient.av_connectionGranted(0, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT)
		mockClient.av_requestPlayerState(0, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT, BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PLAY)

		// it should indicate that we want to play
		verify(musicController).play()

		// trigger app discovery callback
		val callbackCaptor = argumentCaptor<Runnable>()
		verify(musicAppDiscovery).listener = callbackCaptor.capture()
		callbackCaptor.lastValue.run()

		// and so it should have resumed
		verify(musicController).connectAppAutomatically(musicAppInfo)

		// claim that an app is currently playing
		whenever(musicController.musicSessions.getPlayingApp()) doReturn musicAppInfo
	}
}