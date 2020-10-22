package me.hufman.androidautoidrive

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingClient
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.music.*
import me.hufman.androidautoidrive.carapp.music.components.ProgressGaugeAudioState
import me.hufman.androidautoidrive.carapp.music.components.ProgressGaugeToolbarState
import me.hufman.androidautoidrive.carapp.music.views.*
import me.hufman.androidautoidrive.music.*
import me.hufman.androidautoidrive.music.controllers.MusicAppController
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import me.hufman.idriveconnectionkit.rhmi.*
import org.awaitility.Awaitility.await
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.util.*

class TestMusicApp {
	object IDs {
		const val FOCUS_EVENT = 6
		const val ENTRYBUTTON_ACTION = 382
		const val ENTRYBUTTON_DEST_STATE = 384
		const val APPLIST_STATE = 9
		const val APPLIST_ID5_STATE = 11
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
		const val IMAGEID_SHUFFLE_ON = 151
		const val IMAGEID_SHUFFLE_OFF = 158

		const val PLAYBACK_STATE = 16
		const val APPICON_MODEL = 470
		const val COVERART_LARGE_COMPONENT = 89
		const val COVERART_LARGE_MODEL = 469
		const val COVERART_SMALL_COMPONENT = 100
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
		const val QUEUE_STATE = 19
		const val QUEUE_ID5_STATE = 12
		const val QUEUE_STATE_TEXT_MODEL = 526
		const val QUEUE_LIST_COMPONENT = 133
		const val QUEUE_IMAGE_COMPONENT = 132
		const val QUEUE_LIST_MODEL = 532
		const val QUEUE_IMAGE_MODEL = 531
		const val QUEUE_TITLE_MODEL = 527
		const val QUEUE_TITLE_COMPONENT = 135
		const val QUEUE_SUBTITLE_MODEL = 528
		const val QUEUE_SUBTITLE_COMPONENT = 136

		const val TOOLBAR_ACTION_BUTTON = 115
		const val ACTION_STATE = 21
		const val ACTION_ID5_STATE = 18
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

		const val AUDIO_STATE = 24
		const val AUDIOSTATE_TITLE_MODEL = 444
		const val AUDIOSTATE_PROVIDER_MODEL = 457
		const val AUDIOSTATE_COVERART_MODEL = 456
		const val AUDIOSTATE_ARTIST_MODEL = 445
		const val AUDIOSTATE_ALBUM_MODEL = 446
		const val AUDIOSTATE_TRACK_MODEL = 447
		const val AUDIOSTATE_CURRENT_TIME_MODEL = 451
		const val AUDIOSTATE_MAX_TIME_MODEL = 452
		const val AUDIOSTATE_PLAYBACK_PROGRESS_MODEL = 462
		const val AUDIOSTATE_PLAYLIST_MODEL = 458
		const val AUDIOSTATE_PLAYLIST_INDEX_MODEL = 459
	}

	val handler = mock<Handler> {
		on { looper } doAnswer { Looper.myLooper() }
	}

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
		AppSettings.tempSetSetting(AppSettings.KEYS.AUDIO_FORCE_CONTEXT, "true")
	}

	@Before
	fun setup() {
		UnicodeCleaner._addPlaceholderEmoji("\uD83D\uDC08", listOf("cat2"), "cat")
		UnicodeCleaner._addPlaceholderEmoji("\uD83D\uDE3B", listOf("heart_eyes_cat"), "heart_eyes_cat")
	}

	@Test
	fun testAppInit() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = MusicApp(securityAccess, carAppResources, MusicImageIDsMultimedia, phoneAppResources, graphicsHelpers, musicAppDiscovery, musicController, mock())

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
		assertNull(playbackView.repeatButton)
		assertEquals(IDs.APPLIST_STATE, state.toolbarComponentsList[0].getAction()?.asHMIAction()?.getTargetState()?.id)
		assertEquals(IDs.QUEUE_STATE, state.toolbarComponentsList[2].getAction()?.asHMIAction()?.getTargetState()?.id)
		assertEquals(IDs.ACTION_STATE, state.toolbarComponentsList[4].getAction()?.asHMIAction()?.getTargetState()?.id)
		assertEquals(IDs.APPICON_MODEL, playbackView.appLogoModel!!.id)
		assertEquals(IDs.COVERART_LARGE_MODEL, playbackView.albumArtBigModel.id)
		assertEquals(IDs.COVERART_SMALL_MODEL, playbackView.albumArtSmallModel?.id)
		assertEquals(setOf(IDs.ARTIST_LARGE_MODEL, IDs.ARTIST_SMALL_MODEL), playbackView.artistModel.members.map { it?.id }.toSet())
		assertEquals(setOf(IDs.ALBUM_LARGE_MODEL, IDs.ALBUM_SMALL_MODEL), playbackView.albumModel.members.map { it?.id }.toSet())
		assertEquals(setOf(IDs.TRACK_LARGE_MODEL, IDs.TRACK_SMALL_MODEL), playbackView.trackModel.members.map { it?.id }.toSet())
		assertEquals(setOf(IDs.TIME_NUMBER_LARGE_MODEL, IDs.TIME_NUMBER_SMALL_MODEL), playbackView.currentTimeModel.members.map { it?.id }.toSet())
		assertEquals(setOf(IDs.MAXTIME_NUMBER_LARGE_MODEL, IDs.MAXTIME_NUMBER_SMALL_MODEL), playbackView.maximumTimeModel.members.map { it?.id }.toSet())
		assertEquals(setOf(IDs.TIME_GAUGE_MODEL), (playbackView.gaugeModel as ProgressGaugeToolbarState).model.members.map { it?.id }.toSet())
		assertEquals(true, playbackView.queueToolbarButton.properties[RHMIProperty.PropertyId.BOOKMARKABLE.id]?.value)
		assertEquals(true, playbackView.customActionButton.properties[RHMIProperty.PropertyId.BOOKMARKABLE.id]?.value)
	}

	@Test
	fun testAppInitPlaybackViewID5() {
		whenever(carAppResources.getUiDescription()).doAnswer { this.javaClass.classLoader.getResourceAsStream("ui_description_multimedia_v3.xml") }
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = MusicApp(securityAccess, carAppResources, MusicImageIDsSpotify, phoneAppResources, graphicsHelpers, musicAppDiscovery, musicController, mock())
		val playbackView = app.playbackView
		val state = playbackView.state as RHMIState.AudioHmiState
		assertEquals(IDs.AUDIOSTATE_TITLE_MODEL, playbackView.appTitleModel.id)
		assertEquals(IDs.APPLIST_ID5_STATE, state.toolbarComponentsList[0].getAction()?.asHMIAction()?.getTargetState()?.id)
		assertEquals(IDs.QUEUE_ID5_STATE, state.toolbarComponentsList[2].getAction()?.asHMIAction()?.getTargetState()?.id)
		assertNotNull(playbackView.repeatButton)
		assertEquals(IDs.ACTION_ID5_STATE, state.toolbarComponentsList[3].getAction()?.asHMIAction()?.getTargetState()?.id)
		assertNull(playbackView.appLogoModel)
		assertEquals(IDs.AUDIOSTATE_COVERART_MODEL, playbackView.albumArtBigModel.id)
		assertEquals(null, playbackView.albumArtSmallModel?.id)
		assertEquals(setOf(IDs.AUDIOSTATE_ARTIST_MODEL), playbackView.artistModel.members.map { it?.id }.toSet())
		assertEquals(setOf(IDs.AUDIOSTATE_ALBUM_MODEL), playbackView.albumModel.members.map { it?.id }.toSet())
		assertEquals(setOf(IDs.AUDIOSTATE_TRACK_MODEL), playbackView.trackModel.members.map { it?.id }.toSet())
		assertEquals(setOf(IDs.AUDIOSTATE_CURRENT_TIME_MODEL), playbackView.currentTimeModel.members.map { it?.id }.toSet())
		assertEquals(setOf(IDs.AUDIOSTATE_MAX_TIME_MODEL), playbackView.maximumTimeModel.members.map { it?.id }.toSet())
		assertEquals(IDs.AUDIOSTATE_PLAYBACK_PROGRESS_MODEL, (playbackView.gaugeModel as ProgressGaugeAudioState).model.id)
		val playlist = mockServer.data[IDs.AUDIOSTATE_PLAYLIST_MODEL] as BMWRemoting.RHMIDataTable
		assertEquals(3, playlist.totalRows)
		assertEquals(listOf("Back", "", "Next"), playlist.data.map { it[2] })
		assertEquals(1, state.getPlayListFocusRowModel()?.asRaIntModel()?.value)

		// test disabled buttons in id5 audioHmiState
		whenever(musicController.isSupportedAction(MusicAction.SET_REPEAT_MODE)) doReturn false
		whenever(musicController.isSupportedAction(MusicAction.SET_SHUFFLE_MODE)) doReturn false
		playbackView.redraw()
		assertEquals("Shuffle Unavailable", playbackView.shuffleButton.getTooltipModel()?.asRaDataModel()?.value)
		assertEquals("Repeat Unavailable", playbackView.repeatButton?.getTooltipModel()?.asRaDataModel()?.value)
	}

	@Test
	fun testPlaybackViewID5Interaction() {
		whenever(carAppResources.getUiDescription()).doAnswer { this.javaClass.classLoader.getResourceAsStream("ui_description_multimedia_v3.xml") }
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = MusicApp(securityAccess, carAppResources, MusicImageIDsSpotify, phoneAppResources, graphicsHelpers, musicAppDiscovery, musicController, mock())
		val state = app.playbackView.state as RHMIState.AudioHmiState
		state.toolbarComponentsList[4].getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(0 to true))
		verify(musicController).toggleShuffle()
		state.toolbarComponentsList[5].getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(0 to true))
		verify(musicController).toggleRepeat()
		state.getPlayListAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))
		verify(musicController, times(1)).skipToPrevious()
		state.getPlayListAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 1))
		verify(musicController).seekTo(0)
		state.getPlayListAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 2))
		verify(musicController, times(1)).skipToNext()
	}

	fun testAppInitEnqueueView(enqueuedView: EnqueuedView) {
		assertEquals(IDs.QUEUE_LIST_COMPONENT, enqueuedView.listComponent.id)
		assertEquals(false, enqueuedView.listComponent.properties[RHMIProperty.PropertyId.VALID.id]?.value)
		assertEquals(true, enqueuedView.listComponent.properties[RHMIProperty.PropertyId.VISIBLE.id]?.value)
		assertEquals("57,90,10,*", enqueuedView.listComponent.properties[RHMIProperty.PropertyId.LIST_COLUMNWIDTH.id]?.value)
	}

	fun testAppInitCustomActionsView(customActionsView: CustomActionsView) {
		assertEquals(IDs.ACTION_LIST_COMPONENT, customActionsView.listComponent.id)
		assertEquals("57,0,*", customActionsView.listComponent.properties[RHMIProperty.PropertyId.LIST_COLUMNWIDTH.id]?.value)
	}

	@Test
	fun testAppFlow() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val mode = mock<MusicAppMode> {
			on { shouldRequestAudioContext() } doReturn true
		}
		val app = MusicApp(securityAccess, carAppResources, MusicImageIDsMultimedia, phoneAppResources, graphicsHelpers, musicAppDiscovery, musicController, mode)
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
		whenever(musicAppDiscovery.validApps).doAnswer {
			listOf(MusicAppInfo("Test1", mock(), "package", "class"),
					MusicAppInfo("Test2", mock(), "package", "class"))
		}
		argumentCaptor<java.lang.Runnable>().apply {
			verify(musicAppDiscovery).listener = capture()
			lastValue.run()
			// without an InstanceID, won't try to create an AvContext
			assertEquals(0, mockServer.avConnections.size)
			// now set the instanceId
			IDriveConnectionListener.setConnection("", "localhost", 4008, 9)
			lastValue.run()
			assertEquals(1, mockServer.avConnections.size)
			IDriveConnectionListener.reset()
			IDriveConnectionListener.setConnection("", "localhost", 4008, null)
			IDriveConnectionListener.reset()
		}

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
		assertEquals("Drawable{48x48}", (displayedIcons[0] as ByteArray).toString(Charset.defaultCharset()))
		assertEquals("Drawable{48x48}", (displayedIcons[1] as ByteArray).toString(Charset.defaultCharset()))
		assertEquals(listOf("Test1", "Test2"), displayedNames)

		// try clicking an app
		mockClient.rhmi_onActionEvent(1, "unused", IDs.APPLIST_ACTION, mapOf(1.toByte() to 1))
		assertEquals(0, mockServer.avCurrentContext)
		mockClient.av_connectionGranted(0, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT)
		verify(musicController, atLeastOnce()).connectAppManually(argThat { this.name == "Test2" } )
		mockClient.av_requestPlayerState(0, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT, BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PLAY)
		verify(musicController, atLeastOnce()).play()

		// click entrybutton again after an active app is set
		whenever(musicController.currentAppInfo).doReturn(MusicAppInfo("Test2", mock(), "package", "class"))
		whenever(musicController.currentAppController) doReturn mock<MusicAppController>()
		mockClient.rhmi_onActionEvent(1, "unused", IDs.ENTRYBUTTON_ACTION, mapOf(0 to 1))
		assertEquals(IDs.PLAYBACK_STATE, mockServer.data[IDs.ENTRYBUTTON_DEST_STATE])

		// verify that an app listener is connecting, to redraw on changes
		argumentCaptor<Runnable>().apply {
			verify(musicController).listener = capture()
			lastValue.run()
		}

		// test that the playback view redraw didn't happen since it's not focused
		assertFalse(app.playbackViewVisible)
		assertEquals(null, mockServer.data[IDs.ARTIST_LARGE_MODEL])

		// now redraw with the playback view selected
		mockClient.rhmi_onHmiEvent(1, "unused", IDs.PLAYBACK_STATE, 1, mapOf(4.toByte() to true))
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
		assertEquals(mapOf(0.toByte() to IDs.APPLIST_COMPONENT, 41.toByte() to 1), mockServer.triggeredEvents[IDs.FOCUS_EVENT])
	}

	@Test
	fun testGlobalRedraw() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)

		// set the current app and song
		whenever(musicController.currentAppInfo).doReturn(MusicAppInfo("Test2", mock(), "package", "class"))
		whenever(musicController.isConnected()) doReturn true
		whenever(musicController.getMetadata()) doReturn MusicMetadata("testId", queueId=10,
				duration=180000L,
				icon=mock(), coverArt=mock(),
				artist="Artist", album="Album", title="Title")
		val globalState = GlobalMetadata(app, musicController)
		globalState.redraw()

		// verify global metadata happened
		assertEquals("Artist", mockServer.data[IDs.GLOBAL_ARTIST_MODEL])
		assertEquals("Title", mockServer.data[IDs.GLOBAL_TRACK_MODEL])
		assertEquals("Test2", mockServer.data[IDs.GLOBAL_APP_MODEL])
		assertEquals("Title", mockServer.data[IDs.IC_TRACK_MODEL])
		assertEquals("EntICPlaylist", mockServer.data[IDs.IC_USECASE_MODEL])
		assertTrue(mockServer.triggeredEvents.containsKey(IDs.MULTIMEDIA_EVENT))
		assertTrue(mockServer.triggeredEvents.containsKey(IDs.STATUSBAR_EVENT))

		// add a queue
		whenever(musicController.getQueue()) doAnswer { QueueMetadata(null, null, listOf(
				MusicMetadata(queueId=10, title="Song 1"),
				MusicMetadata(queueId=15, title="Song 3"),
				MusicMetadata(queueId=20, title="Song 6")))
		}
		globalState.redraw()
		assertEquals("EntICPlaylist", mockServer.data[IDs.IC_USECASE_MODEL])
		val displayedTitles = (mockServer.data[IDs.IC_PLAYLIST_MODEL] as BMWRemoting.RHMIDataTable).data.map {it[1]}.toTypedArray()
		val displayedChecks = (mockServer.data[IDs.IC_PLAYLIST_MODEL] as BMWRemoting.RHMIDataTable).data.map {it[5]}.toTypedArray()
		assertArrayEquals(arrayOf("< Back", "Title", "Next >", "Song 3", "Song 6"), displayedTitles)
		assertArrayEquals(arrayOf(0, 1, 0, 0, 0), displayedChecks)

		// change song, the checkbox should move
		whenever(musicController.getMetadata()) doReturn MusicMetadata("testId", queueId=20,
				duration=180000L,
				icon=mock(), coverArt=mock(),
				artist="Artist", album="Album", title="Song 6")
		globalState.redraw()
		val displayedTitles2 = (mockServer.data[IDs.IC_PLAYLIST_MODEL] as BMWRemoting.RHMIDataTable).data.map {it[1]}.toTypedArray()
		val displayedChecks2 = (mockServer.data[IDs.IC_PLAYLIST_MODEL] as BMWRemoting.RHMIDataTable).data.map {it[5]}.toTypedArray()
		assertArrayEquals(arrayOf("Song 1", "Song 3", "< Back", "Song 6", "Next >"), displayedTitles2)
		assertArrayEquals(arrayOf(0, 0, 0, 1, 0), displayedChecks2)
	}

	@Test
	fun testPlaybackRedraw() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		var state = app.states[IDs.PLAYBACK_STATE] as RHMIState.ToolbarState
		val playbackView = PlaybackView(state, musicController, mapOf("147.png" to "Placeholder".toByteArray()), phoneAppResources, graphicsHelpers, MusicImageIDsMultimedia)

		whenever(musicController.getQueue()).doAnswer {null}
		whenever(musicController.currentAppInfo).doReturn(MusicAppInfo("Test2", mock(), "package", "class"))

		playbackView.redraw()

		// verify things happened
		verify(musicController, atLeastOnce()).getMetadata()
		assertNotNull(mockServer.data[IDs.APPICON_MODEL])
		assertEquals("Bitmap{320x320}", ((mockServer.data[IDs.COVERART_LARGE_MODEL] as BMWRemoting.RHMIResourceData).data as ByteArray).toString(Charset.defaultCharset()))
		assertEquals("Bitmap{200x200}", ((mockServer.data[IDs.COVERART_SMALL_MODEL] as BMWRemoting.RHMIResourceData).data as ByteArray).toString(Charset.defaultCharset()))
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
		whenever(musicController.getQueue()).doAnswer { QueueMetadata(null, null, listOf(MusicMetadata())) }
		// we should redraw even if the song hasn't changed, and the button should be enabled
		playbackView.redraw()
		assertEquals(true, mockServer.properties[IDs.TOOLBAR_QUEUE_BUTTON]!![RHMIProperty.PropertyId.ENABLED.id])

		// don't enable the Custom Actions button for empty custom actions
		assertEquals(false, mockServer.properties[IDs.TOOLBAR_ACTION_BUTTON]!![RHMIProperty.PropertyId.ENABLED.id])
		// add an action
		whenever(musicController.getCustomActions()).doAnswer { listOf(CustomAction("test", "test", "Name", null, null)) }
		// we should redraw even if the song hasn't changed, and the button should be enabled
		playbackView.redraw()
		assertEquals(true, mockServer.properties[IDs.TOOLBAR_ACTION_BUTTON]!![RHMIProperty.PropertyId.ENABLED.id])

		// now test a different song with missing cover art
		whenever(musicController.getMetadata()).doAnswer {
			MusicMetadata("testId", duration = 180000L,
					icon = mock(), coverArt = null,
					artist = "Artist", album = "Album", title = "Title2")
		}
		playbackView.redraw()
		assertEquals(true, mockServer.properties[IDs.COVERART_LARGE_COMPONENT]!![RHMIProperty.PropertyId.VISIBLE.id])
		assertEquals(false, mockServer.properties[IDs.COVERART_SMALL_COMPONENT]!![RHMIProperty.PropertyId.VISIBLE.id])

		// disconnect the app
		whenever(musicController.isConnected()) doReturn false
		whenever(musicController.getMetadata()) doReturn null as MusicMetadata?
		playbackView.redraw()
		// the Artist field should say Not Connected
		assertEquals("<Not Connected>", mockServer.data[IDs.ARTIST_LARGE_MODEL])
		assertEquals("<Not Connected>", mockServer.data[IDs.ARTIST_SMALL_MODEL])
	}

	@Test
	fun testShuffleButtonRedraw() {
		val app = RHMIApplicationConcrete()
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val state = app.states[IDs.PLAYBACK_STATE] as RHMIState.ToolbarState
		val appSwitcherView = AppSwitcherView(app.states[IDs.APPLIST_STATE]!!, musicAppDiscovery, mock(), graphicsHelpers, MusicImageIDsMultimedia)
		val playbackView = PlaybackView(state, musicController, mapOf(), phoneAppResources, graphicsHelpers, MusicImageIDsMultimedia)
		val enqueuedView = EnqueuedView(app.states[IDs.QUEUE_STATE]!!, musicController, graphicsHelpers, MusicImageIDsMultimedia)
		val actionView = CustomActionsView(app.states[IDs.ACTION_STATE]!!, graphicsHelpers, musicController)

		playbackView.initWidgets(appSwitcherView, enqueuedView, mock(), actionView)

		// redraw when the button is supported and not currently shuffling
		whenever(musicController.isSupportedAction(MusicAction.SET_SHUFFLE_MODE)) doAnswer { true }
		whenever(musicController.isShuffling()) doAnswer { false }
		playbackView.redraw()

		assertEquals(L.MUSIC_TURN_SHUFFLE_ON, playbackView.shuffleButton.getTooltipModel()?.asRaDataModel()?.value)
		assertEquals(IDs.IMAGEID_SHUFFLE_OFF, playbackView.shuffleButton.getImageModel()?.asImageIdModel()?.imageId)

		// redraw when the button is supported and currently shuffling
		playbackView.shuffleButton.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(0 to true))
		verify(musicController).toggleShuffle()

		whenever(musicController.isShuffling()) doAnswer { true }
		playbackView.redraw()

		assertEquals(L.MUSIC_TURN_SHUFFLE_OFF, playbackView.shuffleButton.getTooltipModel()?.asRaDataModel()?.value)
		assertEquals(IDs.IMAGEID_SHUFFLE_ON, playbackView.shuffleButton.getImageModel()?.asImageIdModel()?.imageId)
	}

	@Test
	fun testShuffleButtonRedrawUnsupported() {
		val app = RHMIApplicationConcrete()
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val state = app.states[IDs.PLAYBACK_STATE] as RHMIState.ToolbarState
		val appSwitcherView = AppSwitcherView(app.states[IDs.APPLIST_STATE]!!, musicAppDiscovery, mock(), graphicsHelpers, MusicImageIDsMultimedia)
		val playbackView = PlaybackView(state, musicController, mapOf(), phoneAppResources, graphicsHelpers, MusicImageIDsMultimedia)
		val enqueuedView = EnqueuedView(app.states[IDs.QUEUE_STATE]!!, musicController, graphicsHelpers, MusicImageIDsMultimedia)
		val actionView = CustomActionsView(app.states[IDs.ACTION_STATE]!!, graphicsHelpers, musicController)

		playbackView.initWidgets(appSwitcherView, enqueuedView, mock(), actionView)

		whenever(musicController.isSupportedAction(MusicAction.SET_SHUFFLE_MODE)) doAnswer { false }

		//iDrive 4 hiding button
		playbackView.redraw()

		assertEquals(0, playbackView.shuffleButton.getImageModel()?.asImageIdModel()?.imageId)

		//iDrive 5+ hiding button
		// exception throwing logic is not working for tests so assume the isNewerIDrive flag is set properly
		playbackView.isNewerIDrive = true
		playbackView.redraw()

		assertEquals(false, playbackView.shuffleButton.properties[RHMIProperty.PropertyId.VISIBLE.id]?.value)
	}

	@Test
	fun testRepeatButtonRedraw() {
		// repeat button only available in iDrive 5+ audioHmiState
		val app = RHMIApplicationConcrete()
		app.loadFromXML(this.javaClass.classLoader.getResourceAsStream("ui_description_multimedia_v3.xml")?.readBytes() as ByteArray)
		val state = app.states[IDs.AUDIO_STATE] as RHMIState.AudioHmiState
		val appSwitcherView = AppSwitcherView(app.states[IDs.APPLIST_ID5_STATE]!!, musicAppDiscovery, mock(), graphicsHelpers, MusicImageIDsSpotify)
		val playbackView = PlaybackView(state, musicController, mapOf(), phoneAppResources, graphicsHelpers, MusicImageIDsSpotify)
		val enqueuedView = EnqueuedView(app.states[IDs.QUEUE_ID5_STATE]!!, musicController, graphicsHelpers, MusicImageIDsSpotify)
		val actionView = CustomActionsView(app.states[IDs.ACTION_ID5_STATE]!!, graphicsHelpers, musicController)

		playbackView.initWidgets(appSwitcherView, enqueuedView, mock(), actionView)

		// redraw when not repeating and supported
		whenever(musicController.isSupportedAction(MusicAction.SET_REPEAT_MODE)) doAnswer { true }
		whenever(musicController.getRepeatMode()) doReturn RepeatMode.OFF
		playbackView.redraw()

		assertEquals(L.MUSIC_TURN_REPEAT_ALL_ON, playbackView.repeatButton!!.getTooltipModel()?.asRaDataModel()?.value)
		assertEquals(MusicImageIDsSpotify.REPEAT_OFF, playbackView.repeatButton!!.getImageModel()?.asImageIdModel()?.imageId)

		// redraw when repeating all and supported
		playbackView.repeatButton!!.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(0 to true))
		verify(musicController, times(1)).toggleRepeat()

		whenever(musicController.getRepeatMode()) doReturn RepeatMode.ALL
		playbackView.redraw()

		assertEquals(L.MUSIC_TURN_REPEAT_ONE_ON, playbackView.repeatButton!!.getTooltipModel()?.asRaDataModel()?.value)
		assertEquals(MusicImageIDsSpotify.REPEAT_ALL_ON, playbackView.repeatButton!!.getImageModel()?.asImageIdModel()?.imageId)

		// redraw when repeating one and supported
		playbackView.repeatButton!!.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(0 to true))
		verify(musicController, times(2)).toggleRepeat()

		whenever(musicController.getRepeatMode()) doReturn RepeatMode.ONE
		playbackView.redraw()

		assertEquals(L.MUSIC_TURN_REPEAT_OFF, playbackView.repeatButton!!.getTooltipModel()?.asRaDataModel()?.value)
		assertEquals(MusicImageIDsSpotify.REPEAT_ONE_ON, playbackView.repeatButton!!.getImageModel()?.asImageIdModel()?.imageId)

		// redraw when not repeating and supported
		playbackView.repeatButton!!.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(0 to true))
		verify(musicController, times(3)).toggleRepeat()

		whenever(musicController.getRepeatMode()) doReturn RepeatMode.OFF
		playbackView.redraw()

		assertEquals(L.MUSIC_TURN_REPEAT_ALL_ON, playbackView.repeatButton!!.getTooltipModel()?.asRaDataModel()?.value)
		assertEquals(MusicImageIDsSpotify.REPEAT_OFF, playbackView.repeatButton!!.getImageModel()?.asImageIdModel()?.imageId)

		// redraw when not supported
		whenever(musicController.isSupportedAction(MusicAction.SET_REPEAT_MODE)) doAnswer { false }
		playbackView.redraw()

		assertEquals(false, playbackView.repeatButton!!.properties[RHMIProperty.PropertyId.ENABLED.id]?.value)
	}

	@Test
	fun testMusicControl() {
		val app = RHMIApplicationConcrete()
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val state = app.states[IDs.PLAYBACK_STATE] as RHMIState.ToolbarState
		val appSwitcherView = AppSwitcherView(app.states[IDs.APPLIST_STATE]!!, musicAppDiscovery, mock(), graphicsHelpers, MusicImageIDsMultimedia)
		val playbackView = PlaybackView(state, musicController, mapOf(), phoneAppResources, graphicsHelpers, MusicImageIDsMultimedia)
		val enqueuedView = EnqueuedView(app.states[IDs.QUEUE_STATE]!!, musicController, graphicsHelpers, MusicImageIDsMultimedia)
		val actionView = CustomActionsView(app.states[IDs.ACTION_STATE]!!, graphicsHelpers, musicController)

		playbackView.initWidgets(appSwitcherView, enqueuedView, mock(), actionView)

		state.toolbarComponentsList[7].getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(0 to true))
		verify(musicController).skipToNext()
		state.toolbarComponentsList[6].getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(0 to true))
		verify(musicController).skipToPrevious()
	}

	@Test
	fun testQueueView_NullQueue() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val state = app.states[IDs.QUEUE_STATE] as RHMIState.PlainState
		val queueView = EnqueuedView(state, musicController, graphicsHelpers, MusicImageIDsMultimedia)
		val emptyQueueRow = arrayOf("", "", "<Empty Queue>")
		whenever(musicController.getQueue()) doAnswer { null }
		queueView.show()

		val queueList = mockServer.data[IDs.QUEUE_LIST_MODEL] as BMWRemoting.RHMIDataTable
		assertEquals(1, queueList.totalRows)
		assertArrayEquals(emptyQueueRow, queueList.data[0])
		assertEquals(false, mockServer.properties[IDs.QUEUE_LIST_COMPONENT]!![RHMIProperty.PropertyId.ENABLED.id])
		assertEquals(false, mockServer.properties[IDs.QUEUE_LIST_COMPONENT]!![RHMIProperty.PropertyId.SELECTABLE.id])

		assertEquals("Now Playing", mockServer.data[IDs.QUEUE_STATE_TEXT_MODEL])

		assertEquals(false, mockServer.properties[IDs.QUEUE_IMAGE_COMPONENT]!![RHMIProperty.PropertyId.VISIBLE.id])
	}

	@Test
	fun testQueueView_QueueWithEmptyList_WithCoverArtImage_WithTitle() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val state = app.states[IDs.QUEUE_STATE] as RHMIState.PlainState
		val queueView = EnqueuedView(state, musicController, graphicsHelpers, MusicImageIDsMultimedia)
		val emptyQueueRow = arrayOf("", "", "<Empty Queue>")
		val queueTitle = "queue title"
		val queueCoverArt: Bitmap = mock()
		whenever(musicController.getQueue()) doAnswer { QueueMetadata(queueTitle, null, emptyList(), queueCoverArt) }
		val queueCoverArtByteArray = ByteArray(2)
		whenever(graphicsHelpers.compress(queueCoverArt,180,180, quality = 60)) doAnswer { queueCoverArtByteArray }
		queueView.show()

		val queueList = mockServer.data[IDs.QUEUE_LIST_MODEL] as BMWRemoting.RHMIDataTable
		assertEquals(1, queueList.totalRows)
		assertArrayEquals(emptyQueueRow, queueList.data[0])
		assertEquals(false, mockServer.properties[IDs.QUEUE_LIST_COMPONENT]!![RHMIProperty.PropertyId.ENABLED.id])
		assertEquals(false, mockServer.properties[IDs.QUEUE_LIST_COMPONENT]!![RHMIProperty.PropertyId.SELECTABLE.id])

		assertEquals("$queueTitle - ", mockServer.data[IDs.QUEUE_STATE_TEXT_MODEL])

		val queueCoverArtImageComponent = mockServer.data[IDs.QUEUE_IMAGE_MODEL] as BMWRemoting.RHMIResourceData
		assertEquals(queueCoverArtByteArray, queueCoverArtImageComponent.getData())

		assertEquals(queueTitle, mockServer.data[IDs.QUEUE_TITLE_MODEL])
		assertEquals("", mockServer.data[IDs.QUEUE_SUBTITLE_MODEL])
	}

	@Test
	fun testQueueView_QueueWithoutCoverArt_WithTitle() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val state = app.states[IDs.QUEUE_STATE] as RHMIState.PlainState
		val queueView = EnqueuedView(state, musicController, graphicsHelpers, MusicImageIDsMultimedia)
		val emptyQueueRow = arrayOf("", "", "<Empty Queue>")
		val queueTitle = "queue title"
		whenever(musicController.getQueue()) doAnswer { QueueMetadata(queueTitle, null, emptyList(), null) }
		queueView.show()

		val queueList = mockServer.data[IDs.QUEUE_LIST_MODEL] as BMWRemoting.RHMIDataTable
		assertEquals(1, queueList.totalRows)
		assertArrayEquals(emptyQueueRow, queueList.data[0])
		assertEquals(false, mockServer.properties[IDs.QUEUE_LIST_COMPONENT]!![RHMIProperty.PropertyId.ENABLED.id])
		assertEquals(false, mockServer.properties[IDs.QUEUE_LIST_COMPONENT]!![RHMIProperty.PropertyId.SELECTABLE.id])

		assertEquals("$queueTitle - ", mockServer.data[IDs.QUEUE_STATE_TEXT_MODEL])

		assertEquals("", mockServer.data[IDs.QUEUE_TITLE_MODEL])
		assertEquals("", mockServer.data[IDs.QUEUE_SUBTITLE_MODEL])
	}

	@Test
	fun testQueueView_QueueWithCoverArt_WithoutTitle_WithoutSubtitle() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val state = app.states[IDs.QUEUE_STATE] as RHMIState.PlainState
		val queueView = EnqueuedView(state, musicController, graphicsHelpers, MusicImageIDsMultimedia)
		val emptyQueueRow = arrayOf("", "", "<Empty Queue>")
		val queueCoverArt: Bitmap = mock()
		whenever(musicController.getQueue()) doAnswer { QueueMetadata(null, null, emptyList(), queueCoverArt) }
		val queueCoverArtByteArray = ByteArray(2)
		whenever(graphicsHelpers.compress(queueCoverArt,180,180, quality = 60)) doAnswer { queueCoverArtByteArray }
		queueView.show()

		val queueList = mockServer.data[IDs.QUEUE_LIST_MODEL] as BMWRemoting.RHMIDataTable
		assertEquals(1, queueList.totalRows)
		assertArrayEquals(emptyQueueRow, queueList.data[0])
		assertEquals(false, mockServer.properties[IDs.QUEUE_LIST_COMPONENT]!![RHMIProperty.PropertyId.ENABLED.id])
		assertEquals(false, mockServer.properties[IDs.QUEUE_LIST_COMPONENT]!![RHMIProperty.PropertyId.SELECTABLE.id])

		assertEquals("Now Playing", mockServer.data[IDs.QUEUE_STATE_TEXT_MODEL])

		val queueCoverArtImageComponent = mockServer.data[IDs.QUEUE_IMAGE_MODEL] as BMWRemoting.RHMIResourceData
		assertEquals(queueCoverArtByteArray, queueCoverArtImageComponent.getData())

		assertEquals("", mockServer.data[IDs.QUEUE_TITLE_MODEL])
		assertEquals("", mockServer.data[IDs.QUEUE_SUBTITLE_MODEL])
	}

	@Test
	fun testQueueView_QueueFullyPopulated() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val state = app.states[IDs.QUEUE_STATE] as RHMIState.PlainState
		val queueView = EnqueuedView(state, musicController, graphicsHelpers, MusicImageIDsMultimedia)
		val queueTitle = "queue title"
		val queueSubtitle = "queue subtitle"
		val queueCoverArt: Bitmap = mock()
		val musicMetadata1 = MusicMetadata(queueId=10, title="Song 1", artist="Artist 1")
		val musicMetadata2 = MusicMetadata(queueId=15, title="Song 2", artist="Artist 2")
		val musicMetadata3 = MusicMetadata(queueId=20, title="Song 3", artist="Artist 3")
		whenever(musicController.getQueue()) doAnswer { QueueMetadata(queueTitle, queueSubtitle, listOf(
				musicMetadata1,
				musicMetadata2,
				musicMetadata3
		), queueCoverArt) }
		val queueCoverArtByteArray = ByteArray(2)
		whenever(graphicsHelpers.compress(queueCoverArt,180,180, quality = 60)) doAnswer { queueCoverArtByteArray }
		queueView.show()

		val queueList = mockServer.data[IDs.QUEUE_LIST_MODEL] as BMWRemoting.RHMIDataTable
		assertEquals(3, queueList.totalRows)

		val checkmarkResource = queueList.data[0][0] as BMWRemoting.RHMIResourceIdentifier
		val song1Row = queueList.data[0]
		assertEquals(149, checkmarkResource.id)
		assertEquals(BMWRemoting.RHMIResourceType.IMAGEID, checkmarkResource.type)
		assertEquals("", song1Row[1])
		assertEquals("", song1Row[2])
		assertEquals(musicMetadata1.title+"\n"+musicMetadata1.artist, song1Row[3])

		val song2Row = queueList.data[1]
		assertEquals("", song2Row[0])
		assertEquals("", song2Row[1])
		assertEquals("", song2Row[2])
		assertEquals(musicMetadata2.title+"\n"+musicMetadata2.artist, song2Row[3])

		val song3Row = queueList.data[2]
		assertEquals("", song3Row[0])
		assertEquals("", song3Row[1])
		assertEquals("", song3Row[2])
		assertEquals(musicMetadata3.title+"\n"+musicMetadata3.artist, song3Row[3])

		assertEquals(true, mockServer.properties[IDs.QUEUE_LIST_COMPONENT]!![RHMIProperty.PropertyId.ENABLED.id])
		assertEquals(true, mockServer.properties[IDs.QUEUE_LIST_COMPONENT]!![RHMIProperty.PropertyId.SELECTABLE.id])

		assertEquals("$queueTitle - $queueSubtitle", mockServer.data[IDs.QUEUE_STATE_TEXT_MODEL])

		val queueCoverArtImageComponent = mockServer.data[IDs.QUEUE_IMAGE_MODEL] as BMWRemoting.RHMIResourceData
		assertEquals(queueCoverArtByteArray, queueCoverArtImageComponent.getData())

		assertEquals(queueTitle, mockServer.data[IDs.QUEUE_TITLE_MODEL])
		assertEquals(queueSubtitle, mockServer.data[IDs.QUEUE_SUBTITLE_MODEL])
	}

	@Test
	fun testQueueRedraw_QueueDifferentThanCurrent() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val state = app.states[IDs.QUEUE_STATE] as RHMIState.PlainState
		val queueView = EnqueuedView(state, musicController, graphicsHelpers, MusicImageIDsMultimedia)
		val queueTitle = "queue title"
		val queueSubtitle = "queue subtitle"
		val queueCoverArt: Bitmap = mock()
		val musicMetadata1 = MusicMetadata(queueId=10, title="Song 1", artist="Artist 1")
		val musicMetadata2 = MusicMetadata(queueId=15, title="Song 2", artist="Artist 2")
		val musicMetadata3 = MusicMetadata(queueId=20, title="Song 3", artist="Artist 3")
		whenever(musicController.getQueue()) doAnswer { QueueMetadata(queueTitle, queueSubtitle, listOf(
				musicMetadata1,
				musicMetadata2,
				musicMetadata3
		), queueCoverArt) }

		queueView.show()

		val newQueueTitle = "new queue title"
		whenever(musicController.getQueue()) doAnswer { QueueMetadata(newQueueTitle, null, listOf(
				musicMetadata1,
				musicMetadata2
		), null) }

		queueView.redraw()

		val queueList = mockServer.data[IDs.QUEUE_LIST_MODEL] as BMWRemoting.RHMIDataTable
		assertEquals(2, queueList.totalRows)

		val checkmarkResource = queueList.data[0][0] as BMWRemoting.RHMIResourceIdentifier
		val song1Row = queueList.data[0]
		assertEquals(149, checkmarkResource.id)
		assertEquals(BMWRemoting.RHMIResourceType.IMAGEID, checkmarkResource.type)
		assertEquals("", song1Row[1])
		assertEquals("", song1Row[2])
		assertEquals(musicMetadata1.title+"\n"+musicMetadata1.artist, song1Row[3])

		val song2Row = queueList.data[1]
		assertEquals("", song2Row[0])
		assertEquals("", song2Row[1])
		assertEquals("", song2Row[2])
		assertEquals(musicMetadata2.title+"\n"+musicMetadata2.artist, song2Row[3])

		assertEquals("$newQueueTitle - ", mockServer.data[IDs.QUEUE_STATE_TEXT_MODEL])

		assertEquals("", mockServer.data[IDs.QUEUE_TITLE_MODEL])
		assertEquals("", mockServer.data[IDs.QUEUE_SUBTITLE_MODEL])
	}

	@Test
	fun testQueueRedraw_DifferentSongPlayingThanCurrent() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val state = app.states[IDs.QUEUE_STATE] as RHMIState.PlainState
		val queueView = EnqueuedView(state, musicController, graphicsHelpers, MusicImageIDsMultimedia)
		val musicMetadata1 = MusicMetadata(queueId=10, title="Song 1", artist="Artist 1", mediaId="mediaId1")
		val musicMetadata2 = MusicMetadata(queueId=15, title="Song 2", artist="Artist 2", mediaId="mediaId2")
		val musicMetadata3 = MusicMetadata(queueId=20, title="Song 3", artist="Artist 3", mediaId="mediaId3")
		whenever(musicController.getQueue()) doAnswer { QueueMetadata(null, null, listOf(
				musicMetadata1,
				musicMetadata2,
				musicMetadata3
		), null) }

		queueView.show()

		whenever(musicController.getMetadata()) doAnswer { musicMetadata3 }

		queueView.redraw()

		queueView.songsListAdapter

		val queueList = mockServer.data[IDs.QUEUE_LIST_MODEL] as BMWRemoting.RHMIDataTable

		val checkmarkResource = queueList.data[0][0] as BMWRemoting.RHMIResourceIdentifier
		assertEquals(149, checkmarkResource.id)
		assertEquals(BMWRemoting.RHMIResourceType.IMAGEID, checkmarkResource.type)
		val song3Row = queueList.data[0]
		assertEquals("", song3Row[1])
		assertEquals("", song3Row[2])
		assertEquals(musicMetadata3.title+"\n"+musicMetadata3.artist, song3Row[3])
	}

	@Test
	fun testQueueRedraw_CurrentlyVisibleRowsChanged() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val state = app.states[IDs.QUEUE_STATE] as RHMIState.PlainState
		val queueView = EnqueuedView(state, musicController, graphicsHelpers, MusicImageIDsMultimedia)
		val mockMusicMetadata2: MusicMetadata = mock()
		val musicMetadata1 = MusicMetadata(queueId=10, title="Song 1", artist="Artist 1", mediaId = "mediaId1")
		val musicMetadata3 = MusicMetadata(queueId=20, title="Song 3", artist="Artist 3", mediaId = "mediaId3")
		whenever(musicController.getQueue()) doAnswer { QueueMetadata(null, null, listOf(
				musicMetadata1,
				mockMusicMetadata2,
				musicMetadata3
		), null) }
		val song2Title = "Song 2"
		val song2Artist = "Artist 2"
		whenever(mockMusicMetadata2.queueId) doAnswer { 15 }
		whenever(mockMusicMetadata2.title) doAnswer { song2Title }
		whenever(mockMusicMetadata2.artist) doAnswer { song2Artist }
		whenever(mockMusicMetadata2.mediaId) doAnswer { "mediaId2" }

		queueView.show()
		// trigger the request data callback
		app.components[IDs.QUEUE_LIST_COMPONENT]?.requestDataCallback?.onRequestData(0, 10)

		val coverArt: Bitmap = mock()
		whenever(mockMusicMetadata2.coverArt) doAnswer { coverArt }
		val song2CoverArtImage: ByteArray = byteArrayOf(0x1, 0x2)
		whenever(graphicsHelpers.compress(coverArt, 90, 90, quality = 30)) doAnswer { song2CoverArtImage }

		queueView.redraw()

		val queueList = mockServer.data[IDs.QUEUE_LIST_MODEL] as BMWRemoting.RHMIDataTable
		val song2Row = queueList.data[1]
		assertEquals("", song2Row[0])
		assertEquals(song2CoverArtImage, song2Row[1])
		assertEquals("", song2Row[2])
		assertEquals(song2Title+"\n"+song2Artist, song2Row[3])
	}

	@Test
	fun testQueueRedraw_CurrentlyVisibleRowsSame() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val state = app.states[IDs.QUEUE_STATE] as RHMIState.PlainState
		val queueView = EnqueuedView(state, musicController, graphicsHelpers, MusicImageIDsMultimedia)
		val mockMusicMetadata2: MusicMetadata = mock()
		val musicMetadata1 = MusicMetadata(queueId=10, title="Song 1", artist="Artist 1", mediaId = "mediaId1")
		val musicMetadata3 = MusicMetadata(queueId=20, title="Song 3", artist="Artist 3", mediaId = "mediaId3")
		whenever(musicController.getQueue()) doAnswer { QueueMetadata(null, null, listOf(
				musicMetadata1,
				mockMusicMetadata2,
				musicMetadata3
		), null) }
		val song2Title = "Song 2"
		val song2Artist = "Artist 2"
		whenever(mockMusicMetadata2.queueId) doAnswer { 15 }
		whenever(mockMusicMetadata2.title) doAnswer { song2Title }
		whenever(mockMusicMetadata2.artist) doAnswer { song2Artist }
		whenever(mockMusicMetadata2.mediaId) doAnswer { "mediaId2" }

		queueView.show()
		// trigger the request data callback
		app.components[IDs.QUEUE_LIST_COMPONENT]?.requestDataCallback?.onRequestData(0, 10)

		queueView.redraw()

		val queueList = mockServer.data[IDs.QUEUE_LIST_MODEL] as BMWRemoting.RHMIDataTable
		val song2Row = queueList.data[1]
		assertEquals("", song2Row[0])
		assertEquals("", song2Row[1])
		assertEquals("", song2Row[2])
		assertEquals(song2Title+"\n"+song2Artist, song2Row[3])
	}

	@Test
	fun testQueueInput() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val state = app.states[IDs.QUEUE_STATE] as RHMIState.PlainState
		val playbackView = PlaybackView(app.states[IDs.PLAYBACK_STATE]!!, musicController, mapOf(), phoneAppResources, graphicsHelpers, MusicImageIDsMultimedia)
		val enqueuedView = EnqueuedView(state, musicController, graphicsHelpers, MusicImageIDsMultimedia)

		val song1Title = "Song 1"
		val song1Artist = "Artist 1"
		val song2Title = "Song 2"
		val song2Artist = "Artist 2"
		val song3Title = "Song 3"
		val song3Artist = "Artist 3"
		whenever(musicController.getQueue()) doAnswer { QueueMetadata(null, null, listOf(
				MusicMetadata(queueId=10, title=song1Title, artist=song1Artist),
				MusicMetadata(queueId=15, title=song2Title, artist=song2Artist),
				MusicMetadata(queueId=20, title=song3Title, artist=song3Artist)
		)) }

		enqueuedView.initWidgets(playbackView)
		enqueuedView.show()

		state.components[IDs.QUEUE_LIST_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 5))
		verify(musicController, never()).playQueue(any())
		state.components[IDs.QUEUE_LIST_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 1))
		verify(musicController).playQueue(MusicMetadata(queueId=15, title=song2Title, artist = song2Artist))
	}

	@Test
	fun testQueueViewQueueTitleAndSubtitle() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val playbackView = PlaybackView(app.states[IDs.PLAYBACK_STATE]!!, musicController, mapOf(), phoneAppResources, graphicsHelpers, MusicImageIDsMultimedia)
		val state = app.states[IDs.QUEUE_STATE] as RHMIState.PlainState
		val queueView = EnqueuedView(state, musicController, graphicsHelpers, MusicImageIDsMultimedia)
		queueView.initWidgets(playbackView)
		val queueTitle = "queue title"
		val queueSubtitle = "queue subtitle"
		val queueCoverArt: Bitmap = mock()
		val musicMetadata1 = MusicMetadata(queueId=10, title="Song 1", artist="Artist 1", mediaId="mediaId1")
		val musicMetadata2 = MusicMetadata(queueId=15, title="Song 2", artist="Artist 2", mediaId="mediaId2")
		val musicMetadata3 = MusicMetadata(queueId=20, title="Song 3", artist="Artist 3", mediaId="mediaId3")
		whenever(musicController.getQueue()) doAnswer { QueueMetadata(queueTitle, queueSubtitle, listOf(
				musicMetadata1,
				musicMetadata2,
				musicMetadata3
		), queueCoverArt) }
		queueView.show()

		// song 1 is selected
		assertEquals(0, queueView.selectedIndex)
		assertEquals(queueTitle, mockServer.data[IDs.QUEUE_TITLE_MODEL])
		assertEquals(queueSubtitle, mockServer.data[IDs.QUEUE_SUBTITLE_MODEL])

		// song 2 is selected
		app.components[IDs.QUEUE_LIST_COMPONENT]?.asList()?.getSelectAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 1))
		assertEquals(1, queueView.selectedIndex)
		assertEquals(false, mockServer.properties[IDs.QUEUE_TITLE_COMPONENT]!![RHMIProperty.PropertyId.VISIBLE.id] as Boolean?)
		assertEquals(false, mockServer.properties[IDs.QUEUE_SUBTITLE_COMPONENT]!![RHMIProperty.PropertyId.VISIBLE.id] as Boolean?)

		// song 1 is selected
		app.components[IDs.QUEUE_LIST_COMPONENT]?.asList()?.getSelectAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))
		assertEquals(0, queueView.selectedIndex)
		assertEquals(true, mockServer.properties[IDs.QUEUE_TITLE_COMPONENT]!![RHMIProperty.PropertyId.VISIBLE.id] as Boolean?)
		assertEquals(true, mockServer.properties[IDs.QUEUE_SUBTITLE_COMPONENT]!![RHMIProperty.PropertyId.VISIBLE.id] as Boolean?)
	}

	@Test
	fun testInstrumentCluster() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val globalState = GlobalMetadata(app, musicController)
		globalState.initWidgets()

		// an empty queue with no actions
		whenever(musicController.isSupportedAction(any())) doReturn false
		globalState.redraw()
		assertEquals("EntICPlaylist", mockServer.data[IDs.IC_USECASE_MODEL])
		assertEquals("Title", mockServer.data[IDs.IC_TRACK_MODEL])
		val emptylist = mockServer.data[IDs.IC_PLAYLIST_MODEL] as BMWRemoting.RHMIDataTable
		assertEquals(1, emptylist.totalRows)
		assertArrayEquals(arrayOf("Title"), emptylist.data.map {it[1]}.toTypedArray())

		// an empty queue WITH actions
		whenever(musicController.isSupportedAction(any())) doReturn true
		globalState.displayedSong = null    // reset from the previous test
		globalState.redraw()
		assertEquals("EntICPlaylist", mockServer.data[IDs.IC_USECASE_MODEL])
		assertEquals("Title", mockServer.data[IDs.IC_TRACK_MODEL])
		val singleList = mockServer.data[IDs.IC_PLAYLIST_MODEL] as BMWRemoting.RHMIDataTable
		assertEquals(3, singleList.totalRows)
		assertArrayEquals(arrayOf("< Back", "Title", "Next >"), singleList.data.map {it[1]}.toTypedArray())

		app.actions[IDs.IC_TRACK_ACTION]?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))
		verify(musicController).skipToPrevious()
		app.actions[IDs.IC_TRACK_ACTION]?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 1))
		verify(musicController).seekTo(0)
		app.actions[IDs.IC_TRACK_ACTION]?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 2))
		verify(musicController).skipToNext()

		// a queue that has the song in place
		whenever(musicController.getQueue()) doAnswer { QueueMetadata(null, null, listOf(
				MusicMetadata(queueId=10, title="Song 1", album="Album", artist="Artist"),
				MusicMetadata(queueId=15, title="Song 3"),
				MusicMetadata(queueId=20, title="Song 6")
		)) }
		globalState.redraw()
		assertEquals("EntICPlaylist", mockServer.data[IDs.IC_USECASE_MODEL])
		val list = mockServer.data[IDs.IC_PLAYLIST_MODEL] as BMWRemoting.RHMIDataTable
		assertEquals(5, list.totalRows)
		assertEquals("< Back", list.data[0][1])
		assertEquals("Title", list.data[1][1])
		assertEquals("Artist", list.data[1][2])
		assertEquals("Album", list.data[1][3])
		assertEquals(1, list.data[1][5])
		assertEquals("Next >", list.data[2][1])
		assertEquals("Song 3", list.data[3][1])
		assertEquals("", list.data[3][2])
		assertEquals("", list.data[3][3])
		assertEquals(0, list.data[3][5])
		assertEquals("Song 6", list.data[4][1])

		app.actions[IDs.IC_TRACK_ACTION]?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))
		verify(musicController, times(2)).skipToPrevious()
		app.actions[IDs.IC_TRACK_ACTION]?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 2))
		verify(musicController, times(2)).skipToNext()
		app.actions[IDs.IC_TRACK_ACTION]?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 4))
		verify(musicController).playQueue(MusicMetadata(queueId=20, title="Song 6"))

		// a queue without the song in place
		whenever(musicController.getQueue()) doAnswer { QueueMetadata(null, null, listOf(
				MusicMetadata(queueId=15, title="Song 3"),
				MusicMetadata(queueId=20, title="Song 6")
		)) }
		globalState.redraw()
		val missingList = mockServer.data[IDs.IC_PLAYLIST_MODEL] as BMWRemoting.RHMIDataTable
		assertArrayEquals(arrayOf("Song 3", "Song 6"), missingList.data.map {it[1]}.toTypedArray())
		assertArrayEquals(arrayOf(0, 0), missingList.data.map {it[5]}.toTypedArray())
	}

	@Test
	fun testBrowsePages() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val playbackView = PlaybackView(app.states[IDs.PLAYBACK_STATE]!!, musicController, mapOf(), phoneAppResources, graphicsHelpers, MusicImageIDsMultimedia)
		val browseView = BrowseView(listOf(app.states[IDs.BROWSE1_STATE]!!, app.states[IDs.BROWSE2_STATE]!!), musicController, MusicImageIDsMultimedia)
		browseView.initWidgets(playbackView, inputState)

		val browseResults = CompletableDeferred<List<MusicMetadata>>()
		whenever(musicController.browseAsync(anyOrNull())) doAnswer {
			browseResults
		}
		whenever(musicController.currentAppInfo) doReturn MusicAppInfo("Test2", mock(), "package", "class")

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

		// verifies that it browses again after a timeout
		await().untilAsserted { verify(musicController, times(1)).browseAsync(anyOrNull()) }
		await().untilAsserted { verify(musicController, times(2)).browseAsync(anyOrNull()) }

		// finish loading
		val browseList = listOf(
				MusicMetadata("testId1", title = "Folder \uD83D\uDC08",
						browseable = true, playable = false),
				MusicMetadata("bonusFolder1", title = "BonusFolder1",
						browseable = true, playable = false),
				MusicMetadata("bonusFolder2", title = "BonusFolder2",
						browseable = true, playable = false),
				MusicMetadata("testId2", title = "File1",
						browseable = false, playable = true),
				MusicMetadata("testId3", title = "File2",
						browseable = false, playable = true)
		)
		browseResults.complete(browseList)
		await().until { (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows == 5 }
		assertEquals(true, mockServer.properties[IDs.BROWSE1_MUSIC_COMPONENT]!![RHMIProperty.PropertyId.ENABLED.id] as Boolean?)    // clickable+
		assertArrayEquals(arrayOf("Filter"),
				(mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).data.map {
					it[2]
				}.toTypedArray()
		)
		assertArrayEquals(arrayOf("Folder :cat2:", "BonusFolder1", "BonusFolder2", "File1", "File2"),
				(mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable).data.map {
					it[2]
				}.toTypedArray()
		)

		// trigger a dynamic showList from the car
		mockServer.data.remove(IDs.BROWSE1_MUSIC_MODEL)
		app.components[IDs.BROWSE1_MUSIC_COMPONENT]?.requestDataCallback?.onRequestData(0, 10)
		assertArrayEquals(arrayOf("Folder :cat2:", "BonusFolder1", "BonusFolder2", "File1", "File2"),
				(mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable).data.map {
					it[2]
				}.toTypedArray()
		)

		// go back into the top level browse, it should not have Jump Back
		val browseResultsAgain = CompletableDeferred<List<MusicMetadata>>() // reset the Browse Deferred
		whenever(musicController.browseAsync(anyOrNull())) doAnswer {
			browseResultsAgain
		}
		page1.show()
		assertArrayEquals(arrayOf("Filter"),
				(mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).data.map {
					it[2]
				}.toTypedArray()
		)
		// verify that it didn't show loading screen, even if the deferred is still loading
		Thread.sleep(1000)
		assertEquals(true, mockServer.properties[IDs.BROWSE1_MUSIC_COMPONENT]!![RHMIProperty.PropertyId.ENABLED.id] as Boolean?)   // still clickable
		assertArrayEquals(arrayOf("Folder :cat2:", "BonusFolder1", "BonusFolder2", "File1", "File2"),
				(mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable).data.map {
					it[2]
				}.toTypedArray()
		)
		// finish loading
		val browseListAgain = listOf(
				MusicMetadata("testId1", title = "Folder",
						browseable = true, playable = false),
				MusicMetadata("bonusFolder1", title = "BonusFolder1",
						browseable = true, playable = false),
				MusicMetadata("bonusFolder2", title = "BonusFolder2",
						browseable = true, playable = false),
				MusicMetadata("testId2", title = "File1",
						browseable = false, playable = true),
				MusicMetadata("testId3", title = "File3",
						browseable = false, playable = true)
		)
		browseResultsAgain.complete(browseListAgain)
		await().untilAsserted {
			assertArrayEquals(arrayOf("Folder", "BonusFolder1", "BonusFolder2", "File1", "File3"),
					(mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable).data.map {
						it[2]
					}.toTypedArray()
			)
		}

		// click the folder (testId1:Folder, to show in BROWSE2)
		app.components[IDs.BROWSE1_MUSIC_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback!!.onActionEvent(mapOf(1.toByte() to 0))
		assertEquals(2, browseView.pageStack.size)
		assertEquals(2, browseView.locationStack.size)
		assertEquals("testId1", browseView.locationStack.last()?.mediaId)
		val page2 = browseView.pageStack.last()
		assertEquals("testId1", page2.browsePageModel.folder?.mediaId)
		assertEquals(IDs.BROWSE2_STATE, page2.state.id)

		page2.show()
		assertEquals("Folder", mockServer.data[IDs.BROWSE2_LABEL_MODEL]) // folder name
		await().until { (mockServer.data[IDs.BROWSE2_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows == 5 }
		// browse results are still resolved from last page, so they show up immediately
		assertEquals(true, mockServer.properties[IDs.BROWSE2_MUSIC_COMPONENT]!![RHMIProperty.PropertyId.ENABLED.id] as Boolean?)
		assertArrayEquals(arrayOf("Folder", "BonusFolder1", "BonusFolder2", "File1", "File3"),
				(mockServer.data[IDs.BROWSE2_MUSIC_MODEL] as BMWRemoting.RHMIDataTable).data.map {
					it[2]
				}.toTypedArray()
		)

		// select a deep folder (bonusFolder1:BonusFolder1, to show in BROWSE1)
		mockServer.data.remove(IDs.BROWSE1_MUSIC_MODEL)
		app.components[IDs.BROWSE2_MUSIC_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback!!.onActionEvent(mapOf(1.toByte() to 1))
		app.states[IDs.BROWSE2_STATE]?.onHmiEvent(1, mapOf(4.toByte() to false))
		app.states[IDs.BROWSE1_STATE]?.onHmiEvent(1, mapOf(4.toByte() to true))
		await().untilAsserted {
			assertEquals(5, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertEquals(3, browseView.pageStack.size)
		assertEquals(listOf(null,
				MusicMetadata("testId1", title = "Folder",	browseable = true, playable = false),
				MusicMetadata("bonusFolder1", title = "BonusFolder1", browseable = true, playable = false)),
				browseView.locationStack)
		assertEquals("should update previouslySelected",
				MusicMetadata("bonusFolder1", title = "BonusFolder1", browseable = true, playable = false),
				browseView.pageStack[1].previouslySelected)

		// click the song
		app.components[IDs.BROWSE1_MUSIC_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback!!.onActionEvent(mapOf(1.toByte() to 3))
		assertEquals(listOf(null,
				MusicMetadata("testId1", title = "Folder",	browseable = true, playable = false),
				MusicMetadata("bonusFolder1", title = "BonusFolder1", browseable = true, playable = false),
				MusicMetadata("testId2", title = "File1", browseable = false, playable = true)),
				browseView.locationStack)
		verify(musicController).playSong(MusicMetadata("testId2", title = "File1",
				browseable = false, playable = true))

		// back out of deep folder (now showing testId1:Folder)
		mockServer.triggeredEvents.remove(6)
		mockServer.data.remove(IDs.BROWSE2_MUSIC_MODEL)
		app.states[IDs.BROWSE1_STATE]?.onHmiEvent(1, mapOf(4.toByte() to false))
		app.states[IDs.BROWSE2_STATE]?.onHmiEvent(1, mapOf(4.toByte() to true))
		await().untilAsserted {
			assertEquals(5, (mockServer.data[IDs.BROWSE2_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertEquals(2, browseView.pageStack.size)
		assertEquals("should retain locationStack when backing out", listOf(null,
				MusicMetadata("testId1", title = "Folder",	browseable = true, playable = false),
				MusicMetadata("bonusFolder1", title = "BonusFolder1", browseable = true, playable = false),
				MusicMetadata("testId2", title = "File1", browseable = false, playable = true)),
				browseView.locationStack)
		assertEquals(MusicMetadata("bonusFolder1", title = "BonusFolder1", browseable = true, playable = false), browseView.pageStack.last().previouslySelected)
		assertEquals(IDs.BROWSE2_MUSIC_COMPONENT, mockServer.triggeredEvents[6]!![0.toByte()])
		assertEquals("Selects previouslySelected", 1, mockServer.triggeredEvents[6]!![41.toByte()])

		// press back again (now showing the null root)
		mockServer.data.remove(IDs.BROWSE1_MUSIC_MODEL)
		app.states[IDs.BROWSE2_STATE]?.onHmiEvent(1, mapOf(4.toByte() to false))
		app.states[IDs.BROWSE1_STATE]?.onHmiEvent(1, mapOf(4.toByte() to true))
		await().untilAsserted {
			assertEquals(5, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertEquals(0, mockServer.triggeredEvents[6]!![41.toByte()])
		assertEquals(listOf(page1), browseView.pageStack)
		assertEquals(listOf(null,
				MusicMetadata("testId1", title = "Folder",	browseable = true, playable = false),
				MusicMetadata("bonusFolder1", title = "BonusFolder1", browseable = true, playable = false),
				MusicMetadata("testId2", title = "File1", browseable = false, playable = true)),
				browseView.locationStack)

		// select a different folder (bonusFolder2:BonusFolder2)
		mockServer.data.remove(IDs.BROWSE2_MUSIC_MODEL)
		app.components[IDs.BROWSE1_MUSIC_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback!!.onActionEvent(mapOf(1.toByte() to 2))
		app.states[IDs.BROWSE1_STATE]?.onHmiEvent(1, mapOf(4.toByte() to false))
		app.states[IDs.BROWSE2_STATE]?.onHmiEvent(1, mapOf(4.toByte() to true))
		await().untilAsserted {
			assertEquals(5, (mockServer.data[IDs.BROWSE2_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertEquals(2, browseView.pageStack.size)
		assertEquals(listOf(null,
				MusicMetadata("bonusFolder2", title = "BonusFolder2", browseable = true, playable = false)),
				browseView.locationStack)       // did we truncate the lastSelected stack properly?
		assertEquals("updates previouslySelected",
				MusicMetadata("bonusFolder2", title = "BonusFolder2", browseable = true, playable = false),
				browseView.pageStack[0].previouslySelected)

		// now if we go back, it should update the lastSelected of the main view of the first page
		mockServer.data.remove(IDs.BROWSE1_MUSIC_MODEL)
		app.states[IDs.BROWSE2_STATE]?.onHmiEvent(1, mapOf(4.toByte() to false))
		app.states[IDs.BROWSE1_STATE]?.onHmiEvent(1, mapOf(4.toByte() to true))
		await().untilAsserted {
			assertEquals(5, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertEquals(2, mockServer.triggeredEvents[6]!![41.toByte()])
	}

	@Test
	fun testBrowseShortcut() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val playbackView = PlaybackView(app.states[IDs.PLAYBACK_STATE]!!, musicController, mapOf(), phoneAppResources, graphicsHelpers, MusicImageIDsMultimedia)
		val browseView = BrowseView(listOf(app.states[IDs.BROWSE1_STATE]!!, app.states[IDs.BROWSE2_STATE]!!), musicController, MusicImageIDsMultimedia)
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
	}

	@Test
	fun testBrowseJumpBack() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val playbackView = PlaybackView(app.states[IDs.PLAYBACK_STATE]!!, musicController, mapOf(), phoneAppResources, graphicsHelpers, MusicImageIDsMultimedia)
		val browseView = BrowseView(listOf(app.states[IDs.BROWSE1_STATE]!!, app.states[IDs.BROWSE2_STATE]!!), musicController, MusicImageIDsMultimedia)
		browseView.initWidgets(playbackView, inputState)

		whenever(musicController.browseAsync(null)) doAnswer {
			CompletableDeferred(listOf (
					MusicMetadata("specialId1", title = "Play All",	browseable = false, playable = true),
					MusicMetadata("folderDeep1", title = "Folder deep1", browseable = true, playable = false),
					MusicMetadata("testId1", title = "File1", browseable = false, playable = true)
			))
		}
		whenever(musicController.browseAsync(MusicMetadata("folderDeep1", title = "Folder deep1", browseable = true, playable = false))) doAnswer {
			CompletableDeferred(listOf (
					MusicMetadata("folderDeep2", title = "Folder deep2", browseable = true, playable = false),
					MusicMetadata("testId2", title = "Play All",	browseable = false, playable = true),
					MusicMetadata("testId3", title = "File1", browseable = false, playable = true)
			))
		}
		whenever(musicController.browseAsync(MusicMetadata("folderDeep2", title = "Folder deep2", browseable = true, playable = false))) doAnswer {
			CompletableDeferred(listOf (
					MusicMetadata("folderDeep3", title = "Folder deep3", browseable = true, playable = false),
					MusicMetadata("testId2", title = "Play All",	browseable = false, playable = true),
					MusicMetadata("testId3", title = "File1", browseable = false, playable = true)
			))
		}
		whenever(musicController.browseAsync(MusicMetadata("folderDeep3", title = "Folder deep3", browseable = true, playable = false))) doAnswer {
			CompletableDeferred(listOf (
					MusicMetadata("testId2", title = "Play All",	browseable = false, playable = true),
					MusicMetadata("testId3", title = "File1", browseable = false, playable = true)
			))
		}
		whenever(musicController.browseAsync(MusicMetadata("testId3", title = "File1", browseable = false, playable = true))) doAnswer {
			CompletableDeferred(listOf ())
		}

		val page1 = browseView.pushBrowsePage(null)
		page1.show()
		await().untilAsserted {
			assertEquals(3, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		// make sure the Jump Back action isn't showing
		assertEquals(1, (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).totalRows)
		assertArrayEquals(arrayOf("", "", "Filter"), (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).data[0])
		assertEquals("", (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable).data[0][0])    // not checked

		// now show with a previous location stack
		mockServer.data.remove(IDs.BROWSE1_MUSIC_MODEL)
		browseView.stack.add(BrowseState(MusicMetadata("folderDeep1", title = "Folder deep1", browseable = true, playable = false)))
		browseView.stack.add(BrowseState(MusicMetadata("folderDeep2", title = "Folder deep2", browseable = true, playable = false)))
		page1.previouslySelected = browseView.locationStack[1]
		page1.show()
		await().untilAsserted {
			assertEquals(3, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertArrayEquals(arrayOf("", "", "Jump Back"), (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).data[0])
		assertEquals("Folder deep1", (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable).data[1][2]) // checked
		assertNotEquals("", (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable).data[1][0]) // checked

		// try clicking the action
		app.components[IDs.BROWSE1_ACTIONS_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))
		assertEquals(IDs.BROWSE2_STATE, app.components[IDs.BROWSE1_ACTIONS_COMPONENT]?.asList()?.getAction()?.asHMIAction()?.getTargetState()?.id)
		assertEquals(2, browseView.pageStack.size)
		val page2 = browseView.pageStack[1]
		page2.show()
		await().untilAsserted {
			assertEquals(3, (mockServer.data[IDs.BROWSE2_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}

		// test what happens when a deeper directory is clicked inside Jump Back
		browseView.pushBrowsePage(MusicMetadata("folderDeep3", title = "Folder deep3", browseable = true, playable = false))
		assertEquals(4, browseView.locationStack.size)

		// back out of deep dir
		app.states[IDs.BROWSE1_STATE]?.onHmiEvent(1, mapOf(4.toByte() to false))
		app.states[IDs.BROWSE2_STATE]?.onHmiEvent(1, mapOf(4.toByte() to true))

		// back out of jump back
		app.states[IDs.BROWSE2_STATE]?.onHmiEvent(1, mapOf(4.toByte() to false))
		app.states[IDs.BROWSE1_STATE]?.onHmiEvent(1, mapOf(4.toByte() to true))

		// ensure that the browse stack didn't change after clicking Jump Back
		assertEquals(4, browseView.locationStack.size)
		assertEquals("folderDeep3", browseView.locationStack.last()?.mediaId)

		// test what happens when a song is clicked after jumping back
		app.components[IDs.BROWSE1_ACTIONS_COMPONENT]?.asList()?.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = 0
		mockServer.data.remove(IDs.BROWSE1_MUSIC_MODEL)
		mockServer.data.remove(IDs.BROWSE2_MUSIC_MODEL)
		browseView.stack.add(BrowseState(MusicMetadata("testId3", title = "File1", browseable = false, playable = true)))
		page1.show()
		await().untilAsserted {
			assertEquals(3, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertArrayEquals(arrayOf("", "", "Jump Back"), (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).data[0])
		app.components[IDs.BROWSE1_ACTIONS_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))
		assertEquals(IDs.BROWSE2_STATE, app.components[IDs.BROWSE1_ACTIONS_COMPONENT]?.asList()?.getAction()?.asHMIAction()?.getTargetState()?.id)
		assertEquals(2, browseView.pageStack.size)
		assertEquals("folderDeep3", browseView.pageStack.last().browsePageModel.folder?.mediaId)
		assertEquals(listOf(null, "folderDeep1", "folderDeep2", "folderDeep3", "testId3"), browseView.stack.map {it.location?.mediaId})
		browseView.pageStack.last().show()
		await().untilAsserted {
			assertEquals(2, (mockServer.data[IDs.BROWSE2_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}

		// now test what happens at a fresh new browse, everything should all be in place
		browseView.clearPages()
		val freshpage = browseView.pushBrowsePage(null)
		assertEquals(listOf(null, "folderDeep1", "folderDeep2", "folderDeep3", "testId3"), browseView.stack.map {it.location?.mediaId})
		assertEquals("folderDeep1", freshpage.previouslySelected?.mediaId)
	}

	@Test
	fun testBrowseFilter() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val playbackView = PlaybackView(app.states[IDs.PLAYBACK_STATE]!!, musicController, mapOf(), phoneAppResources, graphicsHelpers, MusicImageIDsMultimedia)
		val browseView = BrowseView(listOf(app.states[IDs.BROWSE1_STATE]!!, app.states[IDs.BROWSE2_STATE]!!), musicController, MusicImageIDsMultimedia)
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
				MusicMetadata("testId4", title = "New song \uD83D\uDC08", browseable = false, playable = true)
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
		assertArrayEquals(arrayOf(arrayOf("New song :cat2:"), arrayOf("Best snew song")), (mockServer.data[IDs.INPUT_SUGGEST_MODEL] as BMWRemoting.RHMIDataTable).data)

		// select a suggestion
		app.components[IDs.INPUT_COMPONENT]?.asInput()?.getSuggestAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 1))
		assertEquals(IDs.PLAYBACK_STATE, app.components[IDs.INPUT_COMPONENT]?.asInput()?.getSuggestAction()?.asHMIAction()?.getTargetState()?.id)
		verify(musicController).playSong(MusicMetadata("testId5", title = "Best snew song", browseable = false, playable = true))

		// try entering an emoji
		app.components[IDs.INPUT_COMPONENT]?.asInput()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(8.toByte() to "delall"))
		app.components[IDs.INPUT_COMPONENT]?.asInput()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(8.toByte() to "cat"))
		assertArrayEquals(arrayOf(arrayOf("New song :cat2:")), (mockServer.data[IDs.INPUT_SUGGEST_MODEL] as BMWRemoting.RHMIDataTable).data)
	}

	@Test
	fun testSearch() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val playbackView = PlaybackView(app.states[IDs.PLAYBACK_STATE]!!, musicController, mapOf(), phoneAppResources, graphicsHelpers, MusicImageIDsMultimedia)
		val browseView = BrowseView(listOf(app.states[IDs.BROWSE1_STATE]!!, app.states[IDs.BROWSE2_STATE]!!), musicController, MusicImageIDsMultimedia)
		browseView.initWidgets(playbackView, app.states[IDs.INPUT_STATE]!!)

		// prepare results
		val browseResults = CompletableDeferred<List<MusicMetadata>>()
		whenever(musicController.browseAsync(anyOrNull())) doAnswer { browseResults }
		val searchResults = CompletableDeferred<List<MusicMetadata>>()
		whenever(musicController.searchAsync(anyOrNull())) doAnswer { searchResults }

		// pretend that the app isn't searchable
		whenever(musicController.currentAppInfo) doReturn MusicAppInfo("Test2", mock(), "package", "class")
		val page1 = browseView.pushBrowsePage(null)
		page1.show()

		// wait for the loading screen to show up
		await().untilAsserted {
			assertEquals(1, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertEquals(0, (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).totalRows)    // should not show Filter or Search

		// now pretend that the app IS searchable
		whenever(musicController.currentAppInfo).doReturn(
				MusicAppInfo("Test2", mock(), "package", "class").apply { searchable = true}
		)
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
		assertArrayEquals(arrayOf(arrayOf("", "", "Search"), arrayOf("", "", "Filter")), (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).data)

		// try clicking the action
		app.components[IDs.BROWSE1_ACTIONS_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))
		assertEquals(IDs.INPUT_STATE, app.components[IDs.BROWSE1_ACTIONS_COMPONENT]?.asList()?.getAction()?.asHMIAction()?.getTargetState()?.id)
		// there should be action handlers now
		assertNotNull(app.components[IDs.INPUT_COMPONENT]?.asInput()?.getAction())
		assertNotNull(app.components[IDs.INPUT_COMPONENT]?.asInput()?.getSuggestAction())

		// try entering a too-short query
		app.components[IDs.INPUT_COMPONENT]?.asInput()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(8.toByte() to "n"))
		verify(musicController, never()).searchAsync(any())

		// enter a longer query
		app.components[IDs.INPUT_COMPONENT]?.asInput()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(8.toByte() to "mario"))
		await().untilAsserted { verify(musicController, times(1)).searchAsync(any()) }

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
	fun testSearchTimeout() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val playbackView = PlaybackView(app.states[IDs.PLAYBACK_STATE]!!, musicController, mapOf(), phoneAppResources, graphicsHelpers, MusicImageIDsMultimedia)
		val browseView = BrowseView(listOf(app.states[IDs.BROWSE1_STATE]!!, app.states[IDs.BROWSE2_STATE]!!), musicController, MusicImageIDsMultimedia)
		browseView.initWidgets(playbackView, app.states[IDs.INPUT_STATE]!!)
		val browsePageView = browseView.pushBrowsePage(null, null)
		val inputState = app.states[IDs.INPUT_STATE]!!
		val inputComponent = app.components[IDs.INPUT_COMPONENT]?.asInput()!!

		val searchResults = CompletableDeferred<List<MusicMetadata>>()
		whenever(musicController.searchAsync(anyOrNull())) doAnswer { searchResults }

		browsePageView.showSearchInput(inputState)

		inputComponent.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(8.toByte() to "mario"))
		await().untilAsserted { verify(musicController, times(1)).searchAsync(any()) }

		await().untilAsserted {
			assertArrayEquals(arrayOf(arrayOf("<Searching>")), (mockServer.data[IDs.INPUT_SUGGEST_MODEL] as BMWRemoting.RHMIDataTable?)?.data)
		}
		// verifies that the Searching entry isn't clickable
		mockServer.data.remove(inputComponent.getSuggestAction()?.asHMIAction()?.targetModel)
		try {
			inputComponent.getSuggestAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(0.toByte() to 1))
			fail()
		} catch (e: RHMIActionAbort) { // don't succeed the RAAction
		}
		assertEquals(0, app.components[IDs.INPUT_COMPONENT]?.asInput()?.getSuggestAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value)

		// verify that it retries
		await().untilAsserted { verify(musicController, times(2)).searchAsync(any()) }
		// verify that it times out
		await().untilAsserted {
			assertArrayEquals(arrayOf(arrayOf("<Empty>")), (mockServer.data[IDs.INPUT_SUGGEST_MODEL] as BMWRemoting.RHMIDataTable?)?.data)
		}

		// verifies that the Empty entry isn't clickable
		mockServer.data.remove(inputComponent.getSuggestAction()?.asHMIAction()?.targetModel)
		try {
			inputComponent.getSuggestAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(0.toByte() to 1))
			fail()
		} catch (e: RHMIActionAbort) { // don't succeed the RAAction
		}
		assertEquals(0, inputComponent.getSuggestAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value)
	}

	@Test
	fun testPlayFromSearch() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val playbackView = PlaybackView(app.states[IDs.PLAYBACK_STATE]!!, musicController, mapOf(), phoneAppResources, graphicsHelpers, MusicImageIDsMultimedia)
		val browseView = BrowseView(listOf(app.states[IDs.BROWSE1_STATE]!!, app.states[IDs.BROWSE2_STATE]!!), musicController, MusicImageIDsMultimedia)
		browseView.initWidgets(playbackView, app.states[IDs.INPUT_STATE]!!)

		// prepare results
		val browseResults = CompletableDeferred<List<MusicMetadata>>().apply {complete(LinkedList())}
		whenever(musicController.browseAsync(anyOrNull())) doAnswer { browseResults }
		val searchResults = CompletableDeferred<List<MusicMetadata>>().apply {complete(LinkedList())}
		whenever(musicController.searchAsync(anyOrNull())) doAnswer { searchResults }

		// pretend that the app isn't searchable
		whenever(musicController.currentAppInfo) doReturn MusicAppInfo("Test2", mock(), "package", "class")
		val page1 = browseView.pushBrowsePage(null)
		page1.show()

		// wait for the loading screen to show up
		await().untilAsserted {
			assertEquals(1, (mockServer.data[IDs.BROWSE1_MUSIC_MODEL] as BMWRemoting.RHMIDataTable?)?.totalRows)
		}
		assertEquals(0, (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).totalRows)    // should not show Filter or Search

		// now pretend that the app IS searchable
		whenever(musicController.isSupportedAction(MusicAction.PLAY_FROM_SEARCH)) doReturn true
		page1.show()
		assertArrayEquals(arrayOf(arrayOf("", "", "Search")), (mockServer.data[IDs.BROWSE1_ACTIONS_MODEL] as BMWRemoting.RHMIDataTable).data)

		// try clicking the action
		app.components[IDs.BROWSE1_ACTIONS_COMPONENT]?.asList()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))
		assertEquals(IDs.INPUT_STATE, app.components[IDs.BROWSE1_ACTIONS_COMPONENT]?.asList()?.getAction()?.asHMIAction()?.getTargetState()?.id)

		// enter a query
		app.components[IDs.INPUT_COMPONENT]?.asInput()?.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(8.toByte() to "mario"))
		await().untilAsserted { verify(musicController).searchAsync(any()) }

		// search results should show the Play From Search option
		await().untilAsserted {
			assertNotNull(mockServer.data[IDs.INPUT_SUGGEST_MODEL] as BMWRemoting.RHMIDataTable?)
		}
		assertArrayEquals(arrayOf(arrayOf("Play From Search")), (mockServer.data[IDs.INPUT_SUGGEST_MODEL] as BMWRemoting.RHMIDataTable?)?.data)

		// try clicking the option
		app.components[IDs.INPUT_COMPONENT]?.asInput()?.getSuggestAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))
		assertEquals(IDs.PLAYBACK_STATE, app.components[IDs.INPUT_COMPONENT]?.asInput()?.getSuggestAction()?.asHMIAction()?.getTargetState()?.id)
		verify(musicController).playFromSearch("mario")
	}

	@Test
	fun testCustomActions() {
		val mockServer = MockBMWRemotingServer()
		val app = RHMIApplicationEtch(mockServer, 1)
		app.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		val playbackView = PlaybackView(app.states[IDs.PLAYBACK_STATE]!!, musicController, mapOf(), phoneAppResources, graphicsHelpers, MusicImageIDsMultimedia)
		val actionView = CustomActionsView(app.states[IDs.ACTION_STATE]!!, graphicsHelpers, musicController)
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

	@Test
	fun testMusicSessions() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = MusicApp(securityAccess, carAppResources, MusicImageIDsMultimedia, phoneAppResources, graphicsHelpers, musicAppDiscovery, musicController, mock())
		val mockClient = IDriveConnection.mockRemotingClient as BMWRemotingClient

		val discoveryListenerCapture = argumentCaptor<Runnable>()
		verify(musicAppDiscovery).listener = discoveryListenerCapture.capture()
		verify(musicAppDiscovery, atLeastOnce()).discoverApps() // discover apps when it starts up

		// tell the app about the current list of apps, without showing the app list
		whenever(musicAppDiscovery.validApps) doAnswer {
			listOf(MusicAppInfo("Test1", mock(), "package", "class"),
					MusicAppInfo("Test2", mock(), "package", "class"))
		}
		discoveryListenerCapture.lastValue.run()
		assertNull("Didn't send an app list to the car", mockServer.data[IDs.APPLIST_LISTMODEL])

		// show the app list
		mockClient.rhmi_onHmiEvent(1, "unused", IDs.APPLIST_STATE, 1, mapOf(4.toByte() to true))
		verify(musicAppDiscovery, atLeastOnce()).discoverAppsAsync() // discover apps when the App List is shown
		val displayedNames = (mockServer.data[IDs.APPLIST_LISTMODEL] as BMWRemoting.RHMIDataTable).data.map {
			it[2]
		}
		assertEquals("Updates the app list in the car", listOf("Test1", "Test2"), displayedNames)

		// add a new app to the list
		whenever(musicAppDiscovery.validApps) doAnswer {
			listOf(MusicAppInfo("Test1", mock(), "package", "class"),
					MusicAppInfo("Test3", mock(), "package3", "class"))
		}
		discoveryListenerCapture.lastValue.run()
		val displayedNamesNew = (mockServer.data[IDs.APPLIST_LISTMODEL] as BMWRemoting.RHMIDataTable).data.map {
			it[2]
		}
		assertEquals("Updates the app list in the car", listOf("Test1", "Test3"), displayedNamesNew)
		(mockServer.data[IDs.APPLIST_LISTMODEL] as BMWRemoting.RHMIDataTable).data.forEach { row ->
			assertEquals("No checkbox in app list", "", row[0])
		}

		// a new music app starts playing, which we don't know about
		val nowPlayingApp = MusicAppInfo("Test4", mock(), "package4", "UNUSED")
		whenever(musicController.musicSessions) doAnswer {
			mock<MusicSessions> {
				on { getPlayingApp() } doReturn nowPlayingApp
			}
		}
		discoveryListenerCapture.lastValue.run()
		verify(musicController).connectAppAutomatically(same(nowPlayingApp))
		// async sets the musicBrowser to the correct connection
		whenever(musicController.currentAppInfo).doReturn(nowPlayingApp)
		whenever(musicAppDiscovery.validApps) doAnswer {
			listOf(MusicAppInfo("Test1", mock(), "package", "class"),
					MusicAppInfo("Test3", mock(), "package3", "class"),
					MusicAppInfo("Test4", mock(), "package4", null))
		}
		app.redraw()
		assertNotEquals("Sets the checkbox in the app list", "", (mockServer.data[IDs.APPLIST_LISTMODEL] as BMWRemoting.RHMIDataTable).data[2][0])

		// a new music session that we know can browse opens up
		reset(musicController)
		whenever(musicController.musicSessions) doAnswer {
			mock<MusicSessions> {
				on { getPlayingApp() } doReturn nowPlayingApp
			}
		}
		val browseableApp = MusicAppInfo("Test4", mock(), "package4", "class")
		whenever(musicAppDiscovery.validApps) doAnswer {
			listOf(MusicAppInfo("Test1", mock(), "package", "class"),
					browseableApp)
		}
		discoveryListenerCapture.lastValue.run()
		verify(musicController).connectAppAutomatically(same(browseableApp))
	}
}