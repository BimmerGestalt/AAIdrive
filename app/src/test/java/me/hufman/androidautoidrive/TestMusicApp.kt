package me.hufman.androidautoidrive

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingClient
import me.hufman.androidautoidrive.carapp.music.GlobalMetadata
import me.hufman.androidautoidrive.carapp.music.MusicApp
import me.hufman.androidautoidrive.carapp.music.views.AppSwitcherView
import me.hufman.androidautoidrive.carapp.music.views.EnqueuedView
import me.hufman.androidautoidrive.carapp.music.views.PlaybackView
import me.hufman.androidautoidrive.music.*
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.SecurityService
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationConcrete
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationEtch
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

		const val NOTIFICATIONICON_EVENT = 5
		const val MULTIMEDIA_EVENT = 576
		const val STATUSBAR_EVENT = 577
		const val GLOBAL_IMAGEID_MODEL = 565
		const val GLOBAL_TRACK_MODEL = 569
		const val GLOBAL_ARTIST_MODEL = 570
		const val GLOBAL_APP_MODEL = 571
		const val IC_TRACK_MODEL = 539
		const val IC_PLAYLIST_MODEL = 534
		const val IC_TRACK_ACTION = 365
		const val IC_USECASE_MODEL = 535
		const val IMAGEID_AUDIO = 161

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

		const val TOOLBAR_QUEUE_BUTTON = 113
		const val QUEUE_STATE = 10
		const val QUEUE_COMPONENT = 39
		const val QUEUE_MODEL = 407
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
		on { getBitmap(isA<Bitmap>(), any(), any()) } doReturn ByteArray(0)
	}

	val musicAppDiscovery = mock<MusicAppDiscovery>()

	val musicController = mock<MusicController> {
		on { getMetadata() } doReturn MusicMetadata("testId", queueId=10,
				duration=180000L,
				icon=mock(), coverArt=mock(),
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
		testAppInitEnqueueView(app.enqueuedView)
	}

	fun testAppInitSwitcher(appSwitcherView: AppSwitcherView) {
		assertEquals("Apps", appSwitcherView.state.getTextModel()?.asRaDataModel()?.value)
		assertEquals(IDs.APPLIST_COMPONENT, appSwitcherView.listApps.id)
		assertEquals(true, appSwitcherView.listApps.properties[RHMIProperty.PropertyId.VISIBLE.id]?.value)
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

	fun testAppInitEnqueueView(enqueuedView: EnqueuedView) {
		assertEquals("Now Playing", enqueuedView.state.getTextModel()?.asRaDataModel()?.value)
		assertEquals(IDs.QUEUE_COMPONENT, enqueuedView.listComponent.id)
		assertEquals(true, enqueuedView.listComponent.properties[RHMIProperty.PropertyId.VISIBLE.id]?.value)
		assertEquals("57,50,*", enqueuedView.listComponent.properties[RHMIProperty.PropertyId.LIST_COLUMNWIDTH.id]?.value)
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
		verify(musicController, atLeastOnce()).connectApp(argThat { this.name == "Test2" } )

		// click entrybutton again after an active app is set
		whenever(musicController.currentApp).then {
			mock<MusicBrowser> {
				on { musicAppInfo } doReturn MusicAppInfo("Test2", mock(), "package", "class")
			}
		}
		mockClient.rhmi_onActionEvent(1, "unused", IDs.ENTRYBUTTON_ACTION, mapOf(0 to 1))
		assertEquals(IDs.PLAYBACK_STATE, mockServer.data[IDs.ENTRYBUTTON_DEST_STATE])

		// verify that an app listener is connecting, to redraw on changes
		val controllerListenerCapture = ArgumentCaptor.forClass(Runnable::class.java)
		verify(musicController).listener = controllerListenerCapture.capture()
		controllerListenerCapture.value.run()

		// test that the playback view redraw didn't happen since it's not focused
		assertFalse(app.playbackViewVisible)
		assertEquals(null, mockServer.data[IDs.ARTIST_LARGE_MODEL])

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

		// verify global metadata happened
		assertEquals("Artist", mockServer.data[IDs.GLOBAL_ARTIST_MODEL])
		assertEquals("Title", mockServer.data[IDs.GLOBAL_TRACK_MODEL])
		assertEquals("Test2", mockServer.data[IDs.GLOBAL_APP_MODEL])
		assertEquals("Title", mockServer.data[IDs.IC_TRACK_MODEL])
		assertTrue(mockServer.triggeredEvents.containsKey(IDs.MULTIMEDIA_EVENT))
		assertTrue(mockServer.triggeredEvents.containsKey(IDs.STATUSBAR_EVENT))

		// show the app window again, with an app selected
		mockClient.rhmi_onHmiEvent(1, "unused", IDs.APPLIST_STATE, 1, mapOf(4.toByte() to true))
		assertEquals(mockServer.triggeredEvents[IDs.FOCUS_EVENT], mapOf(0.toByte() to IDs.APPLIST_COMPONENT, 41.toByte() to 1))
	}

	@Test
	fun testPlaybackRedraw() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		var state = app.states[IDs.PLAYBACK_STATE] as RHMIState.ToolbarState
		val playbackView = PlaybackView(state, musicController, phoneAppResources)

		whenever(musicController.getQueue()).doAnswer {null}
		whenever(musicController.currentApp).then {
			mock<MusicBrowser> {
				on { musicAppInfo } doReturn MusicAppInfo("Test2", mock(), "package", "class")
			}
		}

		playbackView.redraw()

		// verify things happened
		verify(musicController, atLeastOnce()).getMetadata()
		assertNotNull(mockServer.data[IDs.APPICON_MODEL])
		assertNotNull(mockServer.data[IDs.COVERART_LARGE_MODEL])
		assertNotNull(mockServer.data[IDs.COVERART_SMALL_MODEL])
		assertEquals("Artist", mockServer.data[IDs.ARTIST_LARGE_MODEL])
		assertEquals("Artist", mockServer.data[IDs.ARTIST_SMALL_MODEL])
		assertEquals("Album", mockServer.data[IDs.ALBUM_LARGE_MODEL])
		assertEquals("Title", mockServer.data[IDs.TRACK_LARGE_MODEL])
		assertEquals("Title", mockServer.data[IDs.TRACK_SMALL_MODEL])
		assertEquals(2, mockServer.data[IDs.TIME_GAUGE_MODEL])
		assertEquals("  0:05", mockServer.data[IDs.TIME_NUMBER_SMALL_MODEL])
		assertEquals("  0:05", mockServer.data[IDs.TIME_NUMBER_LARGE_MODEL])
		assertEquals("  3:00", mockServer.data[IDs.MAXTIME_NUMBER_SMALL_MODEL])
		assertEquals("  3:00", mockServer.data[IDs.MAXTIME_NUMBER_LARGE_MODEL])

		// don't enable the queue button for an empty queue
		assertEquals(false, mockServer.properties[IDs.TOOLBAR_QUEUE_BUTTON]!![RHMIProperty.PropertyId.ENABLED.id])

		// now we have a queue!
		whenever(musicController.getQueue()).doAnswer { listOf(MusicMetadata()) }
		// we only redraw the queue button with a changed song
		whenever(musicController.getMetadata()).doAnswer {
			MusicMetadata("testId", duration = 180000L,
					icon = mock(), coverArt = mock(),
					artist = "Artist", album = "Album", title = "Title2")
		}

		playbackView.redraw()
		assertEquals(true, mockServer.properties[IDs.TOOLBAR_QUEUE_BUTTON]!![RHMIProperty.PropertyId.ENABLED.id])
	}

	@Test
	fun testMusicControl() {
		val app = RHMIApplicationConcrete()
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		var state = app.states[IDs.PLAYBACK_STATE] as RHMIState.ToolbarState
		val appSwitcherView = AppSwitcherView(app.states[IDs.APPLIST_STATE]!!, musicAppDiscovery, mock(), phoneAppResources)
		val playbackView = PlaybackView(state, musicController, phoneAppResources)
		val enqueuedView = EnqueuedView(app.states[IDs.QUEUE_STATE]!!, musicController, phoneAppResources)

		playbackView.initWidgets(appSwitcherView, enqueuedView)
		state.toolbarComponentsList[7].getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(0 to true))
		verify(musicController).skipToNext()
		state.toolbarComponentsList[6].getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(0 to true))
		verify(musicController).skipToPrevious()
	}

	@Test
	fun testQueueRedraw() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		var state = app.states[IDs.QUEUE_STATE] as RHMIState.PlainState
		val queueView = EnqueuedView(state, musicController, phoneAppResources)

		run {
			whenever(musicController.getQueue()) doAnswer { LinkedList() }
			queueView.show()
			val list = mockServer.data[IDs.QUEUE_MODEL] as BMWRemoting.RHMIDataTable
			assertEquals(1, list.totalRows)
			assertArrayEquals(arrayOf("", "", "<Empty Queue>"), list.data[0])
			assertEquals(false, mockServer.properties[IDs.QUEUE_COMPONENT]!![RHMIProperty.PropertyId.ENABLED.id])
		}

		run {
			whenever(musicController.getQueue()) doAnswer { listOf(
					MusicMetadata(queueId=10, title="Song 1"),
					MusicMetadata(queueId=15, title="Song 3"),
					MusicMetadata(queueId=20, title="Song 6")
			) }
			queueView.show()
			val list = mockServer.data[IDs.QUEUE_MODEL] as BMWRemoting.RHMIDataTable
			assertEquals(3, list.totalRows)
			assertArrayEquals(arrayOf("", "", "Song 1"), list.data[0])
			assertArrayEquals(arrayOf("", "", "Song 3"), list.data[1])
			assertArrayEquals(arrayOf("", "", "Song 6"), list.data[2])
			assertEquals(true, mockServer.properties[IDs.QUEUE_COMPONENT]!![RHMIProperty.PropertyId.ENABLED.id])
		}
	}

	@Test
	fun testQueueInput() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		var state = app.states[IDs.QUEUE_STATE] as RHMIState.PlainState
		val playbackView = PlaybackView(app.states[IDs.PLAYBACK_STATE]!!, musicController, phoneAppResources)
		val enqueuedView = EnqueuedView(state, musicController, phoneAppResources)

		whenever(musicController.getQueue()) doAnswer { listOf(
				MusicMetadata(queueId=10, title="Song 1"),
				MusicMetadata(queueId=15, title="Song 3"),
				MusicMetadata(queueId=20, title="Song 6")
		) }

		enqueuedView.initWidgets(playbackView)
		enqueuedView.show()

		state.components[IDs.QUEUE_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 5))
		verify(musicController, never()).playQueue(any())
		state.components[IDs.QUEUE_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 1))
		verify(musicController).playQueue(15)
	}

	@Test
	fun testInstrumentCluster() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val globalState = GlobalMetadata(app, musicController)
		globalState.initWidgets()

		globalState.redraw()
		assertEquals("", mockServer.data[IDs.IC_USECASE_MODEL])
		assertEquals("Title", mockServer.data[IDs.IC_TRACK_MODEL])

		whenever(musicController.getQueue()) doAnswer { listOf(
				MusicMetadata(queueId=10, title="Song 1", album="Album", artist="Artist"),
				MusicMetadata(queueId=15, title="Song 3"),
				MusicMetadata(queueId=20, title="Song 6")
		) }
		globalState.redraw()
		assertEquals("EntICPlaylist", mockServer.data[IDs.IC_USECASE_MODEL])
		val list = mockServer.data[IDs.IC_PLAYLIST_MODEL] as BMWRemoting.RHMIDataTable
		assertEquals(3, list.totalRows)
		assertEquals("Song 1", list.data[0][1])
		assertEquals("Artist", list.data[0][2])
		assertEquals("Album", list.data[0][3])
		assertEquals(1, list.data[0][5])
		assertEquals("Song 3", list.data[1][1])
		assertEquals("", list.data[1][2])
		assertEquals("", list.data[1][3])
		assertEquals(0, list.data[1][5])
		assertEquals("Song 6", list.data[2][1])

		app.actions[IDs.IC_TRACK_ACTION]?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 2))
		verify(musicController).playQueue(20)
	}
}