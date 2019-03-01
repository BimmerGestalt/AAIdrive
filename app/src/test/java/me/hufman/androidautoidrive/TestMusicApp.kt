package me.hufman.androidautoidrive

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingClient
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.carapp.music.GlobalMetadata
import me.hufman.androidautoidrive.carapp.music.MusicApp
import me.hufman.androidautoidrive.carapp.music.views.*
import me.hufman.androidautoidrive.music.*
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.SecurityService
import me.hufman.idriveconnectionkit.rhmi.*
import org.awaitility.Awaitility.await
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

		const val TOOLBAR_ACTION_BUTTON = 115
		const val ACTION_STATE = 21
		const val ACTION_LIST_COMPONENT = 141
		const val ACTION_LIST_MODEL = 549

		const val BROWSE1_STATE = 11
		const val BROWSE1_STATE_MODEL = 416
		const val BROWSE2_STATE = 12
		const val BROWSE1_LABEL_COMPONENT = 48
		const val BROWSE1_LABEL_MODEL = 417
		const val BROWSE1_ACTIONS_COMPONENT = 51
		const val BROWSE1_ACTIONS_MODEL = 420
		const val BROWSE1_MUSIC_COMPONENT = 53
		const val BROWSE1_MUSIC_MODEL = 422
		const val BROWSE2_LABEL_COMPONENT = 60
		const val BROWSE2_LABEL_MODEL = 430
		const val BROWSE2_MUSIC_COMPONENT = 65
		const val BROWSE2_MUSIC_MODEL = 435

		const val INPUT_STATE = 17
		const val INPUT_COMPONENT = 119
		const val INPUT_RESULT_MODEL = 512
		const val INPUT_SUGGEST_MODEL = 509
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

	val inputState = mock<RHMIState> {
		on { componentsList } doAnswer {
			mutableListOf(RHMIComponent.Input(mock(), IDs.INPUT_COMPONENT).apply {
				resultModel = IDs.INPUT_RESULT_MODEL
				suggestModel = IDs.INPUT_SUGGEST_MODEL
			})
		}
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
		testAppInitCustomActionsView(app.customActionsView)
	}

	fun testAppInitSwitcher(appSwitcherView: AppSwitcherView) {
		assertEquals("Apps", appSwitcherView.state.getTextModel()?.asRaDataModel()?.value)
		assertEquals(IDs.APPLIST_COMPONENT, appSwitcherView.listApps.id)
		assertEquals(true, appSwitcherView.listApps.properties[RHMIProperty.PropertyId.VISIBLE.id]?.value)
	}

	fun testAppInitPlaybackView(playbackView: PlaybackView) {
		val state = playbackView.state as RHMIState.ToolbarState
		assertEquals(IDs.APPLIST_STATE, state.toolbarComponentsList[0].getAction()?.asHMIAction()?.getTargetState()?.id)
		assertEquals(IDs.QUEUE_STATE, state.toolbarComponentsList[2].getAction()?.asHMIAction()?.getTargetState()?.id)
		assertEquals(IDs.ACTION_STATE, state.toolbarComponentsList[4].getAction()?.asHMIAction()?.getTargetState()?.id)
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

	fun testAppInitCustomActionsView(customActionsView: CustomActionsView) {
		assertEquals(IDs.ACTION_LIST_COMPONENT, customActionsView.listComponent.id)
		assertEquals("57,0,*", customActionsView.listComponent.properties[RHMIProperty.PropertyId.LIST_COLUMNWIDTH.id]?.value)
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
		val actionView = CustomActionsView(app.states[IDs.ACTION_STATE]!!, phoneAppResources, musicController)

		playbackView.initWidgets(appSwitcherView, enqueuedView, mock(), actionView)

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
		verify(musicController).playQueue(MusicMetadata(queueId=15, title="Song 3"))
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
		verify(musicController).playQueue(MusicMetadata(queueId=20, title="Song 6"))
	}

	@Test
	fun testBrowsePages() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val playbackView = PlaybackView(app.states[IDs.PLAYBACK_STATE]!!, musicController, phoneAppResources)
		val browseView = BrowseView(listOf(app.states[IDs.BROWSE1_STATE]!!, app.states[IDs.BROWSE2_STATE]!!), musicController)
		browseView.initWidgets(playbackView, inputState)

		val browseResults = CompletableDeferred<List<MusicMetadata>>()
		whenever(musicController.browseAsync(anyOrNull())) doAnswer {
			browseResults
		}
		val musicAppInfo = mock<MusicBrowser> {
			on { musicAppInfo } doAnswer {
				MusicAppInfo("Test2", mock(), "package", "class")
			}
		}
		whenever(musicController.currentApp) doReturn musicAppInfo
		// start browsing
		val page1 = browseView.pushBrowsePage(null)
		assertEquals(listOf(page1), browseView.pageStack)
		assertEquals(listOf(null), browseView.locationStack)
		assertEquals(IDs.BROWSE1_STATE, page1.state.id)
		assertEquals(true, mockServer.properties[IDs.BROWSE1_LABEL_COMPONENT]!![RHMIProperty.PropertyId.VISIBLE.id] as Boolean?)
		assertEquals(true, mockServer.properties[IDs.BROWSE1_MUSIC_COMPONENT]!![RHMIProperty.PropertyId.VISIBLE.id] as Boolean?)
		page1.show()
		assertEquals(listOf(page1), browseView.pageStack)
		assertEquals("Browse", mockServer.data[IDs.BROWSE1_STATE_MODEL])
		assertEquals("Test2", mockServer.data[IDs.BROWSE1_LABEL_MODEL]) // app name at the top

		await().until { (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows == 1 }    // wait for loader to show

		assertEquals(false, mockServer.properties[IDs.BROWSE1_MUSIC_COMPONENT]!![RHMIProperty.PropertyId.VALID.id] as Boolean?)  // request dynamic paging
		assertEquals(false, mockServer.properties[IDs.BROWSE1_MUSIC_COMPONENT]!![RHMIProperty.PropertyId.ENABLED.id] as Boolean?)   // not clickable
		assertEquals("<Loading>", (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable).data[0][2])
		// finish loading
		browseResults.complete(listOf(
				MusicMetadata("testId1", title = "Folder",
						browseable = true, playable = false),
				MusicMetadata("bonusFolder1", title = "Folder2",
						browseable = true, playable = false),
				MusicMetadata("testId2", title = "File1",
						browseable = false, playable = true),
				MusicMetadata("testId3", title = "File2",
						browseable = false, playable = true)
		))
		await().until { (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows == 4 }
		assertEquals(true, mockServer.properties[IDs.BROWSE1_MUSIC_COMPONENT]!![RHMIProperty.PropertyId.ENABLED.id] as Boolean?)    // clickable
		assertArrayEquals(arrayOf("Folder", "Folder2", "File1", "File2"),
				(mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable).data.map {
					it[2]
				}.toTypedArray()
		)

		// trigger a dynamic showList from the car
		mockServer.data.remove(IDs.BROWSE1_MUSIC_MODEL)
		app.components[IDs.BROWSE1_MUSIC_COMPONENT]?.requestDataCallback?.onRequestData(0, 10)
		assertArrayEquals(arrayOf("Folder", "Folder2", "File1", "File2"),
				(mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable).data.map {
					it[2]
				}.toTypedArray()
		)

		// click the folder
		app.components[IDs.BROWSE1_MUSIC_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback!!.onActionEvent(mapOf(1.toByte() to 0))
		assertEquals(2, browseView.pageStack.size)
		assertEquals(2, browseView.locationStack.size)
		assertEquals("testId1", browseView.locationStack.last()?.mediaId)
		val page2 = browseView.pageStack.last()
		assertEquals("testId1", page2.folder?.mediaId)
		assertEquals(IDs.BROWSE2_STATE, page2.state.id)

		page2.show()
		assertEquals("Folder", mockServer.data[IDs.BROWSE2_LABEL_MODEL]) // folder name
		await().until { (mockServer.data[IDs.BROWSE2_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows == 4 }
		// browse results are still resolved from last page, so they show up immediately
		assertEquals(true, mockServer.properties[IDs.BROWSE2_MUSIC_COMPONENT]!![RHMIProperty.PropertyId.ENABLED.id] as Boolean?)
		assertArrayEquals(arrayOf("Folder", "Folder2", "File1", "File2"),
				(mockServer.data[IDs.BROWSE2_MUSIC_MODEL] as BMWRemoting.RHMIDataTable).data.map {
					it[2]
				}.toTypedArray()
		)

		// select a deep folder
		mockServer.data.remove(IDs.BROWSE1_MUSIC_MODEL)
		app.components[IDs.BROWSE2_MUSIC_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback!!.onActionEvent(mapOf(1.toByte() to 1))
		app.states[IDs.BROWSE2_STATE]?.onHmiEvent(1, mapOf(4.toByte() to false))
		app.states[IDs.BROWSE1_STATE]?.onHmiEvent(1, mapOf(4.toByte() to true))
		await().untilAsserted {
			assertEquals(4, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertEquals(3, browseView.pageStack.size)
		assertEquals(listOf(null,
				MusicMetadata("testId1", title = "Folder",	browseable = true, playable = false),
				MusicMetadata("bonusFolder1", title = "Folder2", browseable = true, playable = false)),
				browseView.locationStack)
		assertEquals("should update previouslySelected",
				MusicMetadata("bonusFolder1", title = "Folder2", browseable = true, playable = false),
				browseView.pageStack[1].previouslySelected)

		// click the song
		app.components[IDs.BROWSE1_MUSIC_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback!!.onActionEvent(mapOf(1.toByte() to 2))
		assertEquals(listOf(null,
				MusicMetadata("testId1", title = "Folder",	browseable = true, playable = false),
				MusicMetadata("bonusFolder1", title = "Folder2", browseable = true, playable = false),
				MusicMetadata("testId2", title = "File1", browseable = false, playable = true)),
				browseView.locationStack)
		verify(musicController).playSong(MusicMetadata("testId2", title = "File1",
				browseable = false, playable = true))

		// back out of deep folder
		mockServer.data.remove(IDs.BROWSE2_MUSIC_MODEL)
		app.states[IDs.BROWSE1_STATE]?.onHmiEvent(1, mapOf(4.toByte() to false))
		app.states[IDs.BROWSE2_STATE]?.onHmiEvent(1, mapOf(4.toByte() to true))
		await().untilAsserted {
			assertEquals(4, (mockServer.data[IDs.BROWSE2_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertEquals(2, browseView.pageStack.size)
		assertEquals("should retain locationStack when backing out", listOf(null,
				MusicMetadata("testId1", title = "Folder",	browseable = true, playable = false),
				MusicMetadata("bonusFolder1", title = "Folder2", browseable = true, playable = false),
				MusicMetadata("testId2", title = "File1", browseable = false, playable = true)),
				browseView.locationStack)
		assertEquals(IDs.BROWSE2_MUSIC_COMPONENT, mockServer.triggeredEvents[6]!![0.toByte()])
		assertEquals("Selects previouslySelected", 1, mockServer.triggeredEvents[6]!![41.toByte()])

		// press back again
		mockServer.data.remove(IDs.BROWSE1_MUSIC_MODEL)
		app.states[IDs.BROWSE2_STATE]?.onHmiEvent(1, mapOf(4.toByte() to false))
		app.states[IDs.BROWSE1_STATE]?.onHmiEvent(1, mapOf(4.toByte() to true))
		await().untilAsserted {
			assertEquals(4, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertEquals(listOf(page1), browseView.pageStack)
		assertEquals(listOf(null,
				MusicMetadata("testId1", title = "Folder",	browseable = true, playable = false),
				MusicMetadata("bonusFolder1", title = "Folder2", browseable = true, playable = false),
				MusicMetadata("testId2", title = "File1", browseable = false, playable = true)),
				browseView.locationStack)

		// select a different folder
		mockServer.data.remove(IDs.BROWSE2_MUSIC_MODEL)
		app.components[IDs.BROWSE1_MUSIC_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback!!.onActionEvent(mapOf(1.toByte() to 1))
		app.states[IDs.BROWSE1_STATE]?.onHmiEvent(1, mapOf(4.toByte() to false))
		app.states[IDs.BROWSE2_STATE]?.onHmiEvent(1, mapOf(4.toByte() to true))
		await().untilAsserted {
			assertEquals(4, (mockServer.data[IDs.BROWSE2_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertEquals(2, browseView.pageStack.size)
		assertEquals(listOf(null,
				MusicMetadata("bonusFolder1", title = "Folder2", browseable = true, playable = false)),
				browseView.locationStack)       // did we truncate the lastSelected stack properly?
		assertEquals("updates previouslySelected",
				MusicMetadata("bonusFolder1", title = "Folder2", browseable = true, playable = false),
				browseView.pageStack[0].previouslySelected)

		// now if we go back, it should update the lastSelected of the main view of the first page
		mockServer.data.remove(IDs.BROWSE1_MUSIC_MODEL)
		app.states[IDs.BROWSE2_STATE]?.onHmiEvent(1, mapOf(4.toByte() to false))
		app.states[IDs.BROWSE1_STATE]?.onHmiEvent(1, mapOf(4.toByte() to true))
		await().untilAsserted {
			assertEquals(4, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
	}

	@Test
	fun testBrowseShortcut() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val playbackView = PlaybackView(app.states[IDs.PLAYBACK_STATE]!!, musicController, phoneAppResources)
		val browseView = BrowseView(listOf(app.states[IDs.BROWSE1_STATE]!!, app.states[IDs.BROWSE2_STATE]!!), musicController)
		browseView.initWidgets(playbackView, inputState)

		whenever(musicController.browseAsync(null)) doAnswer {
			CompletableDeferred(listOf (
					MusicMetadata("testId1", title = "Play All",	browseable = false, playable = true),
					MusicMetadata("folder1", title = "Folder", browseable = true, playable = false)
			))
		}
		whenever(musicController.browseAsync(MusicMetadata("folder1", title = "Folder", browseable = true, playable = false))) doAnswer {
			CompletableDeferred(listOf (
					MusicMetadata("testId2", title = "Play All",	browseable = false, playable = true),
					MusicMetadata("testId3", title = "File1", browseable = false, playable = true)
			))
		}

		val page1 = browseView.pushBrowsePage(null)
		page1.show()
		await().untilAsserted {
			assertEquals(2, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}

		assertEquals(" / Folder", mockServer.data[IDs.BROWSE1_LABEL_MODEL])
		assertEquals(MusicMetadata("folder1", title = "Folder", browseable = true, playable = false), page1.folder)
	}

	@Test
	fun testBrowseJumpBack() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val playbackView = PlaybackView(app.states[IDs.PLAYBACK_STATE]!!, musicController, phoneAppResources)
		val browseView = BrowseView(listOf(app.states[IDs.BROWSE1_STATE]!!, app.states[IDs.BROWSE2_STATE]!!), musicController)
		browseView.initWidgets(playbackView, inputState)

		whenever(musicController.browseAsync(null)) doAnswer {
			CompletableDeferred(listOf (
					MusicMetadata("specialId1", title = "Play All",	browseable = false, playable = true),
					MusicMetadata("folder1", title = "Folder", browseable = true, playable = false),
					MusicMetadata("testId1", title = "File1", browseable = false, playable = true)
			))
		}
		whenever(musicController.browseAsync(MusicMetadata("folderDeep1", title = "Folder deep", browseable = true, playable = false))) doAnswer {
			CompletableDeferred(listOf (
					MusicMetadata("testId2", title = "Play All",	browseable = false, playable = true),
					MusicMetadata("testId3", title = "File1", browseable = false, playable = true)
			))
		}

		val page1 = browseView.pushBrowsePage(null)
		page1.show()
		await().untilAsserted {
			assertEquals(3, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		// make sure the Jump Back action isn't showing
		assertEquals(1, (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).totalRows)
		assertArrayEquals(arrayOf("", "", "Filter"), (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).data[0])

		// now show with a previous location stack
		mockServer.data.remove(IDs.BROWSE1_MUSIC_MODEL)
		browseView.locationStack.add(MusicMetadata("folderDeep1", title = "Folder deep", browseable = true, playable = false))
		page1.show()
		await().untilAsserted {
			assertEquals(3, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertArrayEquals(arrayOf("", "", "Jump Back"), (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).data[0])

		// try clicking the action
		app.components[IDs.BROWSE1_ACTIONS_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))
		assertEquals(IDs.BROWSE2_STATE, app.components[IDs.BROWSE1_ACTIONS_COMPONENT]?.asList()?.getAction()?.asHMIAction()?.getTargetState()?.id)
		assertEquals(2, browseView.pageStack.size)
		val page2 = browseView.pageStack[1]
		page2.show()
		await().untilAsserted {
			assertEquals(2, (mockServer.data[IDs.BROWSE2_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
	}

	@Test
	fun testBrowseFilter() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val playbackView = PlaybackView(app.states[IDs.PLAYBACK_STATE]!!, musicController, phoneAppResources)
		val browseView = BrowseView(listOf(app.states[IDs.BROWSE1_STATE]!!, app.states[IDs.BROWSE2_STATE]!!), musicController)
		browseView.initWidgets(playbackView, app.states[IDs.INPUT_STATE]!!)

		val browseResults = CompletableDeferred<List<MusicMetadata>>()
		whenever(musicController.browseAsync(anyOrNull())) doAnswer { browseResults }

		val page1 = browseView.pushBrowsePage(null)
		page1.show()

		// wait for the loading screen to show up
		await().untilAsserted {
			assertEquals(1, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertEquals(0, (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).totalRows)    // should not show Filter

		// then finish loading
		browseResults.complete(listOf (
				MusicMetadata("testId2", title = "Play All",	browseable = false, playable = true),
				MusicMetadata("testId3", title = "File1", browseable = false, playable = true),
				MusicMetadata("testId5", title = "Best snew song", browseable = false, playable = true),
				MusicMetadata("testId4", title = "New song", browseable = false, playable = true)
		))
		await().untilAsserted {
			assertEquals(1, (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertArrayEquals(arrayOf("", "", "Filter"), (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).data[0])

		// try clicking the action
		app.components[IDs.BROWSE1_ACTIONS_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))
		assertEquals(IDs.INPUT_STATE, app.components[IDs.BROWSE1_ACTIONS_COMPONENT]?.asList()?.getAction()?.asHMIAction()?.getTargetState()?.id)
		// there should be action handlers now
		assertNotNull(app.components[IDs.INPUT_COMPONENT]?.asInput()?.getAction())
		assertNotNull(app.components[IDs.INPUT_COMPONENT]?.asInput()?.getSuggestAction())

		// try entering a query
		app.components[IDs.INPUT_COMPONENT]?.asInput()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(8.toByte() to "n"))
		assertArrayEquals(arrayOf(arrayOf("New song"), arrayOf("Best snew song")), (mockServer.data[IDs.INPUT_SUGGEST_MODEL] as BMWRemoting.RHMIDataTable).data)

		// select a suggestion
		app.components[IDs.INPUT_COMPONENT]?.asInput()?.getSuggestAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 1))
		assertEquals(IDs.PLAYBACK_STATE, app.components[IDs.INPUT_COMPONENT]?.asInput()?.getSuggestAction()?.asHMIAction()?.getTargetState()?.id)
		verify(musicController).playSong(MusicMetadata("testId5", title = "Best snew song", browseable = false, playable = true))
	}

	@Test
	fun testSearch() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val playbackView = PlaybackView(app.states[IDs.PLAYBACK_STATE]!!, musicController, phoneAppResources)
		val browseView = BrowseView(listOf(app.states[IDs.BROWSE1_STATE]!!, app.states[IDs.BROWSE2_STATE]!!), musicController)
		browseView.initWidgets(playbackView, app.states[IDs.INPUT_STATE]!!)

		// prepare results
		val browseResults = CompletableDeferred<List<MusicMetadata>>()
		whenever(musicController.browseAsync(anyOrNull())) doAnswer { browseResults }
		val searchResults = CompletableDeferred<List<MusicMetadata>>()
		whenever(musicController.searchAsync(anyOrNull())) doAnswer { searchResults }

		// pretend that the app isn't searchable
		whenever(musicController.currentApp).then {
			mock<MusicBrowser> {
				on { musicAppInfo } doReturn MusicAppInfo("Test2", mock(), "package", "class")
			}
		}
		val page1 = browseView.pushBrowsePage(null)
		page1.show()

		// wait for the loading screen to show up
		await().untilAsserted {
			assertEquals(1, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertEquals(0, (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).totalRows)    // should not show Filter or Search

		// now pretend that the app IS searchable
		whenever(musicController.currentApp).then {
			mock<MusicBrowser> {
				on { musicAppInfo } doReturn MusicAppInfo("Test2", mock(), "package", "class").apply { searchable = true }
			}
		}
		mockServer.data.remove(IDs.BROWSE1_MUSIC_MODEL)
		page1.show()
		await().untilAsserted {
			assertEquals(1, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertEquals(1, (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).totalRows)    // should not show Filter or Search
		assertArrayEquals(arrayOf(arrayOf("", "", "Search")), (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).data)

		// then finish loading browse
		browseResults.complete(listOf (
				MusicMetadata("testId2", title = "Play All",	browseable = false, playable = true),
				MusicMetadata("testId3", title = "File1", browseable = false, playable = true),
				MusicMetadata("testId5", title = "Best snew song", browseable = false, playable = true),
				MusicMetadata("testId4", title = "New song", browseable = false, playable = true)
		))
		await().untilAsserted {
			assertEquals(4, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertArrayEquals(arrayOf(arrayOf("", "", "Filter"), arrayOf("", "", "Search")), (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).data)

		// try clicking the action
		app.components[IDs.BROWSE1_ACTIONS_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 1))
		assertEquals(IDs.INPUT_STATE, app.components[IDs.BROWSE1_ACTIONS_COMPONENT]?.asList()?.getAction()?.asHMIAction()?.getTargetState()?.id)
		// there should be action handlers now
		assertNotNull(app.components[IDs.INPUT_COMPONENT]?.asInput()?.getAction())
		assertNotNull(app.components[IDs.INPUT_COMPONENT]?.asInput()?.getSuggestAction())

		// try entering a too-short query
		app.components[IDs.INPUT_COMPONENT]?.asInput()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(8.toByte() to "n"))
		verify(musicController, never()).searchAsync(any())

		// enter a longer query
		app.components[IDs.INPUT_COMPONENT]?.asInput()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(8.toByte() to "mario"))
		await().untilAsserted { verify(musicController).searchAsync(any()) }
		assertEquals(0, (mockServer.data[IDs.INPUT_SUGGEST_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)    // nothing until we start showing the loader

		await().untilAsserted {
			assertArrayEquals(arrayOf(arrayOf("<Searching>")), (mockServer.data[IDs.INPUT_SUGGEST_MODEL] as BMWRemoting.RHMIDataTable?)?.data)
		}

		// search results finished loading
		searchResults.complete(listOf(MusicMetadata("testId5", title = "Best snew song", browseable = false, playable = true),
				MusicMetadata("testId4", title = "New song", browseable = false, playable = true)))
		await().untilAsserted {
			assertArrayEquals(arrayOf(arrayOf("Best snew song"), arrayOf("New song")), (mockServer.data[IDs.INPUT_SUGGEST_MODEL] as BMWRemoting.RHMIDataTable?)?.data)
		}

		// select a suggestion
		app.components[IDs.INPUT_COMPONENT]?.asInput()?.getSuggestAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 1))
		assertEquals(IDs.PLAYBACK_STATE, app.components[IDs.INPUT_COMPONENT]?.asInput()?.getSuggestAction()?.asHMIAction()?.getTargetState()?.id)
		verify(musicController).playSong(MusicMetadata("testId4", title = "New song", browseable = false, playable = true))
	}

	@Test
	fun testCustomActions() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val playbackView = PlaybackView(app.states[IDs.PLAYBACK_STATE]!!, musicController, phoneAppResources)
		val actionView = CustomActionsView(app.states[IDs.ACTION_STATE]!!, phoneAppResources, musicController)
		actionView.initWidgets(playbackView)

		whenever(musicController.getCustomActions()) doReturn listOf(CustomAction(
				"packageName", "actionName", "Custom Name", null, null
		))
		actionView.show()
		verify(musicController).getCustomActions()

		assertArrayEquals(arrayOf(arrayOf("", "", "Custom Name")), (mockServer.data[IDs.ACTION_LIST_MODEL] as BMWRemoting.RHMIDataTable).data)
		app.components[IDs.ACTION_LIST_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(
				1.toByte() to 1
		))
		verify(musicController, never()).customAction(any())
		app.components[IDs.ACTION_LIST_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(
				1.toByte() to 0
		))
		verify(musicController).customAction(CustomAction(
				"packageName", "actionName", "Custom Name", null, null
		))
	}
}