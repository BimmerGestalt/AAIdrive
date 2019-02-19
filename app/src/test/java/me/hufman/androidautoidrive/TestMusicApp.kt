package me.hufman.androidautoidrive

import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingClient
import me.hufman.androidautoidrive.carapp.music.AVContextHandler
import me.hufman.androidautoidrive.carapp.music.MusicApp
import me.hufman.androidautoidrive.carapp.music.views.AppSwitcherView
import me.hufman.androidautoidrive.carapp.music.views.PlaybackView
import me.hufman.androidautoidrive.music.*
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.SecurityService
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationConcrete
import me.hufman.idriveconnectionkit.rhmi.RHMIProperty
import me.hufman.idriveconnectionkit.rhmi.RHMIState
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentCaptor
import java.io.ByteArrayInputStream
import java.util.*

class TestMusicApp {
	object IDs {
		const val FOCUS_EVENT = 6
		const val ENTRYBUTTON_ACTION = 382
		const val ENTRYBUTTON_DEST_STATE = 384
		const val APPLIST_STATE = 9
		const val APPLIST_TEXTMODEL = 390
		const val APPLIST_COMPONENT = 27
		const val APPLIST_LISTMODEL = 394
		const val APPLIST_ACTION = 165

		const val PLAYBACK_STATE = 16
		const val APPICON_MODEL = 470
		const val COVERART_LARGE_MODEL = 469
		const val COVERART_SMALL_MODEL = 473
		const val ARTIST_LARGE_MODEL = 477
		const val ARTIST_SMALL_MODEL = 486
		const val ALBUM_LARGE_MODEL = 478
		val ALBUM_SMALL_MODEL = null
		const val TRACK_LARGE_MODEL = 479
		const val TRACK_SMALL_MODEL = 488
		const val TIME_NUMBER_LARGE_MODEL = 480
		const val TIME_NUMBER_SMALL_MODEL = 490
		const val TIME_GAUGE_MODEL = 468
		const val MAXTIME_NUMBER_LARGE_MODEL = 481
		const val MAXTIME_NUMBER_SMALL_MODEL = 491
	}

	val handler = mock<Handler> {
		on { looper } doAnswer { Looper.myLooper() }
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
		on { getBitmap(isA<Drawable>(), any(), any()) } doReturn ByteArray(0)
	}

	val musicAppDiscovery = mock<MusicAppDiscovery>()

	val musicController = mock<MusicController> {
		on { getMetadata() } doReturn MusicMetadata("testId", duration=180000L,
				artist="Artist", album="Album", title="Title")
		on { getPlaybackPosition() } doReturn PlaybackPosition(false, SystemClock.elapsedRealtime(), 5000L, 180000L)
	}

	init {
		AppSettings.loadDefaultSettings()
		SecurityService.activeSecurityConnections["mock"] = mock {
			on { signChallenge(any(), any() )} doReturn ByteArray(512)
		}
	}

	@Test
	fun testAppInit() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = MusicApp( carAppResources, phoneAppResources, musicAppDiscovery, musicController)

		// verify the right elements are selected
		testAppInitSwitcher(app.appSwitcherView)
		testAppInitPlaybackView(app.playbackView)
	}

	fun testAppInitSwitcher(appSwitcherView: AppSwitcherView) {
		assertEquals(IDs.APPLIST_COMPONENT, appSwitcherView.listApps.id)
	}

	fun testAppInitPlaybackView(playbackView: PlaybackView) {
		assertEquals(IDs.APPICON_MODEL, playbackView.appLogoModel.id)
		assertEquals(IDs.COVERART_LARGE_MODEL, playbackView.albumArtBigModel.id)
		assertEquals(IDs.COVERART_SMALL_MODEL, playbackView.albumArtSmallModel.id)
		assertEquals(setOf(IDs.ARTIST_LARGE_MODEL, IDs.ARTIST_SMALL_MODEL), playbackView.artistModel.members.map { it?.id }.toSet())
		assertEquals(setOf(IDs.ALBUM_LARGE_MODEL, IDs.ALBUM_SMALL_MODEL), playbackView.albumModel.members.map { it?.id }.toSet())
		assertEquals(setOf(IDs.TRACK_LARGE_MODEL, IDs.TRACK_SMALL_MODEL), playbackView.trackModel.members.map { it?.id }.toSet())
		assertEquals(setOf(IDs.TIME_NUMBER_LARGE_MODEL, IDs.TIME_NUMBER_SMALL_MODEL), playbackView.currentTimeModel.members.map { it?.id }.toSet())
		assertEquals(setOf(IDs.MAXTIME_NUMBER_LARGE_MODEL, IDs.MAXTIME_NUMBER_SMALL_MODEL), playbackView.maximumTimeModel.members.map { it?.id }.toSet())
		assertEquals(setOf(IDs.TIME_GAUGE_MODEL), playbackView.gaugeModel.members.map { it?.id }.toSet())
	}

	@Test
	fun testAppFlow() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = MusicApp(carAppResources, phoneAppResources, musicAppDiscovery, musicController)
		val mockClient = IDriveConnection.mockRemotingClient as BMWRemotingClient

		// click into the trigger a redraw
		mockClient.rhmi_onActionEvent(1, "unused", IDs.ENTRYBUTTON_ACTION, mapOf(0 to 1))
		mockClient.rhmi_onHmiEvent(1, "unused", IDs.APPLIST_STATE, 1, mapOf(4.toByte() to true))

		// shows the applist on the first click through
		println(mockServer.properties)
		assertEquals(true, mockServer.properties[IDs.APPLIST_COMPONENT]!![RHMIProperty.PropertyId.VISIBLE.id])
		assertEquals(false, mockServer.properties[IDs.APPLIST_COMPONENT]!![RHMIProperty.PropertyId.SELECTABLE.id])
		assertEquals(IDs.APPLIST_STATE, mockServer.data[IDs.ENTRYBUTTON_DEST_STATE])
		assertEquals("Apps", mockServer.data[IDs.APPLIST_TEXTMODEL])
		assertArrayEquals(arrayOf(arrayOf("", "", "<No Apps>")), (mockServer.data[IDs.APPLIST_LISTMODEL] as BMWRemoting.RHMIDataTable).data)

		// some apps are discovered
		whenever(musicAppDiscovery.validApps).then {
			listOf(MusicAppInfo("Test1", mock(), "package", "class"),
					MusicAppInfo("Test2", mock(), "package", "class"))
		}
		val discoveryListenerCapture = ArgumentCaptor.forClass(Runnable::class.java)
		verify(musicAppDiscovery).listener = discoveryListenerCapture.capture()
		discoveryListenerCapture.value.run()
		assertEquals(2, mockServer.avConnections.size)

		// click entrybutton again with a list of apps
		mockClient.rhmi_onActionEvent(1, "unused", IDs.ENTRYBUTTON_ACTION, mapOf(0 to 1))
		mockClient.rhmi_onHmiEvent(1, "unused", IDs.APPLIST_STATE, 1, mapOf(4.toByte() to true))
		assertEquals(true, mockServer.properties[IDs.APPLIST_COMPONENT]!![RHMIProperty.PropertyId.SELECTABLE.id])
		val displayedIcons = (mockServer.data[IDs.APPLIST_LISTMODEL] as BMWRemoting.RHMIDataTable).data.map {
			(it[1] as BMWRemoting.RHMIResourceData).data
		}
		val displayedNames = (mockServer.data[IDs.APPLIST_LISTMODEL] as BMWRemoting.RHMIDataTable).data.map {
			it[2]
		}
		assertTrue(displayedIcons[0] is ByteArray && displayedIcons[0].isEmpty())
		assertTrue(displayedIcons[1] is ByteArray && displayedIcons[1].isEmpty())
		assertEquals(listOf("Test1", "Test2"), displayedNames)

		// try clicking an app
		mockClient.rhmi_onActionEvent(1, "unused", IDs.APPLIST_ACTION, mapOf(1.toByte() to 1))
		assertEquals(1, mockServer.avCurrentContext)
		mockClient.av_connectionGranted(1, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT)
		verify(musicController).connectApp(argThat { this.name == "Test2" } )

		// click entrybutton again after an active app is set
		whenever(musicController.currentApp).then {
			mock<MusicBrowser>()
		}
		mockClient.rhmi_onActionEvent(1, "unused", IDs.ENTRYBUTTON_ACTION, mapOf(0 to 1))
		assertEquals(IDs.PLAYBACK_STATE, mockServer.data[IDs.ENTRYBUTTON_DEST_STATE])

		// verify that an app listener is connecting, to redraw on changes
		val controllerListenerCapture = ArgumentCaptor.forClass(Runnable::class.java)
		verify(musicController).listener = controllerListenerCapture.capture()
		controllerListenerCapture.value.run()

		// test that the playback view redraw didn't happen since it's not focused
		assertFalse(app.playbackViewVisible)
		verify(musicController, never()).getMetadata()

		// now redraw with the playback view selected
		mockClient.rhmi_onHmiEvent(1, "unused", IDs.PLAYBACK_STATE, 11, mapOf(23.toByte() to true))
		assertTrue(app.playbackViewVisible)
		verify(musicController, atLeastOnce()).getMetadata()

		// verify things happened
		assertEquals("Artist", mockServer.data[IDs.ARTIST_LARGE_MODEL])
		assertEquals("Artist", mockServer.data[IDs.ARTIST_SMALL_MODEL])
		assertEquals("Album", mockServer.data[IDs.ALBUM_LARGE_MODEL])
		assertEquals("Title", mockServer.data[IDs.TRACK_LARGE_MODEL])
		assertEquals("Title", mockServer.data[IDs.TRACK_SMALL_MODEL])

		// show the app window again, with an app selected
		mockClient.rhmi_onHmiEvent(1, "unused", IDs.APPLIST_STATE, 1, mapOf(4.toByte() to true))
		assertEquals(mockServer.triggeredEvents[IDs.FOCUS_EVENT], mapOf(0.toByte() to IDs.APPLIST_COMPONENT, 41.toByte() to 1))
	}

	@Test
	fun testMusicControl() {
		val app = RHMIApplicationConcrete()
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		var state = app.states[IDs.PLAYBACK_STATE] as RHMIState.ToolbarState
		val appSwitcherView = AppSwitcherView(app.states[IDs.APPLIST_STATE]!!, musicAppDiscovery, mock(), phoneAppResources)
		val playbackView = PlaybackView(state, musicController, phoneAppResources)
		playbackView.initWidgets(appSwitcherView)
		state.toolbarComponentsList[7].getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(0 to true))
		verify(musicController).skipToNext()
		state.toolbarComponentsList[6].getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(0 to true))
		verify(musicController).skipToPrevious()
	}
}