package me.hufman.androidautoidrive.music.controllers

import android.graphics.Bitmap
import android.util.Base64
import com.adamratzman.spotify.models.PlaylistUri
import com.adamratzman.spotify.models.SpotifyUri
import com.google.gson.Gson
import com.nhaarman.mockito_kotlin.*
import com.spotify.android.appremote.api.*
import com.spotify.protocol.client.CallResult
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.*
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.MockAppSettings
import me.hufman.androidautoidrive.MutableAppSettings
import me.hufman.androidautoidrive.music.*
import me.hufman.androidautoidrive.music.spotify.TemporaryPlaylistState
import me.hufman.androidautoidrive.music.spotify.SpotifyMusicMetadata
import me.hufman.androidautoidrive.music.spotify.SpotifyWebApi
import me.hufman.androidautoidrive.utils.Utils
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpotifyMusicAppControllerTest {
	val contentCallback = argumentCaptor<CallResult.ResultCallback<ListItems>>()
	val imagesCallback = argumentCaptor<CallResult.ResultCallback<Bitmap>>()
	val libraryCallback = argumentCaptor<CallResult.ResultCallback<LibraryState>>()
	val spotifyCallback = argumentCaptor<Subscription.EventCallback<PlayerState>>()
	val playlistCallback = argumentCaptor<Subscription.EventCallback<PlayerContext>>()

	val connectApi = mock<ConnectApi>()
	val contentApi = mock<ContentApi> {
		on { getRecommendedContentItems(any()) } doAnswer {
			val result = mock<CallResult<ListItems>>()
			whenever(result.setResultCallback(contentCallback.capture())) doReturn result
			result
		}
		on { getChildrenOfItem(any(), any(), any()) } doAnswer {
			val result = mock<CallResult<ListItems>>()
			whenever(result.setResultCallback(contentCallback.capture())) doReturn result
			result
		}
	}
	val imagesApi = mock<ImagesApi> {
		on { getImage(any()) } doAnswer {
			val result = mock<CallResult<Bitmap>>()
			whenever(result.setResultCallback(imagesCallback.capture())) doReturn result
			result
		}
		on { getImage(any(), any()) } doAnswer {
			val result = mock<CallResult<Bitmap>>()
			whenever(result.setResultCallback(imagesCallback.capture())) doReturn result
			result
		}
	}
	val playerApi = mock<PlayerApi> {
		on { subscribeToPlayerState() } doAnswer {
			val subscription = mock<Subscription<PlayerState>>()
			whenever(subscription.setEventCallback(spotifyCallback.capture())) doReturn subscription
			subscription
		}
		on { subscribeToPlayerContext() } doAnswer {
			val subscription = mock<Subscription<PlayerContext>>()
			whenever(subscription.setEventCallback(playlistCallback.capture())) doReturn subscription
			subscription
		}
	}
	val userApi = mock<UserApi> {
		on { getLibraryState(any()) } doAnswer {
			val result = mock<CallResult<LibraryState>>()
			whenever(result.setResultCallback(libraryCallback.capture())) doReturn result
			result
		}
	}

	val remote = mock<SpotifyAppRemote> {
		on { connectApi } doReturn connectApi
		on { contentApi } doReturn contentApi
		on { imagesApi } doReturn imagesApi
		on { playerApi } doReturn playerApi
		on { userApi } doReturn userApi
	}

	val webApi = mock<SpotifyWebApi> {
	}

	lateinit var controller: SpotifyAppController
	lateinit var appSettings: MutableAppSettings
	private val testDispatcher = TestCoroutineDispatcher()
	private val gson: Gson = Gson()

	@Before
	fun setup() {
		appSettings = MockAppSettings()
		controller = SpotifyAppController(mock(), remote, webApi, appSettings, false)
		controller.defaultDispatcher = testDispatcher
	}

	@After
	fun tearDown() {
		testDispatcher.cleanupTestCoroutines()
	}

	@Test
	fun testControl() {
		controller.play()
		verify(playerApi).resume()
		verify(connectApi).connectSwitchToLocalDevice()

		controller.pause()
		verify(playerApi).pause()

		controller.skipToPrevious()
		verify(playerApi).skipPrevious()

		controller.skipToNext()
		verify(playerApi).skipNext()

		controller.seekTo(200)
		verify(playerApi).seekTo(200)

		val song = MusicMetadata(mediaId = "test", queueId = 2)
		controller.playSong(song)
		verify(playerApi).play(song.mediaId)
	}

	@Test
	fun testCustomActions() {
		controller.customAction(controller.CUSTOM_ACTION_TURN_REPEAT_ALL_ON)
		verify(playerApi).setRepeat(Repeat.ALL)
		controller.customAction(controller.CUSTOM_ACTION_TURN_REPEAT_ONE_ON)
		verify(playerApi).setRepeat(Repeat.ONE)
		controller.customAction(controller.CUSTOM_ACTION_TURN_REPEAT_ONE_OFF)
		verify(playerApi).setRepeat(Repeat.OFF)

		controller.currentTrack = MusicMetadata(mediaId = "mediaId")
		controller.customAction(controller.CUSTOM_ACTION_ADD_TO_COLLECTION)
		verify(userApi).addToLibrary("mediaId")

		controller.customAction(controller.CUSTOM_ACTION_REMOVE_FROM_COLLECTION)
		verify(userApi).removeFromLibrary("mediaId")
	}

	@Test
	fun testCustomActionNames() {
		assertEquals("Like", controller.CUSTOM_ACTION_ADD_TO_COLLECTION.name)
		assertEquals("Dislike", controller.CUSTOM_ACTION_REMOVE_FROM_COLLECTION.name)
		assertEquals("Make Radio Station", controller.CUSTOM_ACTION_START_RADIO.name)
	}

	@Test
	fun testIsShuffling() {
		// test shuffle is started on app start
		run {
			val state = PlayerState(
					Track(
							Artist("artist", "uri"),
							listOf(Artist("artist", "uri")),
							Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
					false, 1.0f, 200,
					PlayerOptions(true, Repeat.OFF),
					PlayerRestrictions(true, true, true, true, true, true)
			)
			spotifyCallback.lastValue.onEvent(state)

			assertTrue(controller.isShuffling())
		}

		// test shuffle is not started on app start
		run {
			val state = PlayerState(
					Track(
							Artist("artist", "uri"),
							listOf(Artist("artist", "uri")),
							Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
					false, 1.0f, 200,
					PlayerOptions(false, Repeat.OFF),
					PlayerRestrictions(true, true, true, true, true, true)
			)
			spotifyCallback.lastValue.onEvent(state)

			assertFalse(controller.isShuffling())
		}

		// test shuffle bool is flipped upon spotify callback changing
		run {
			val initialState = PlayerState(
					Track(
							Artist("artist", "uri"),
							listOf(Artist("artist", "uri")),
							Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
					false, 1.0f, 200,
					PlayerOptions(false, Repeat.OFF),
					PlayerRestrictions(true, true, true, true, true, true)
			)
			spotifyCallback.lastValue.onEvent(initialState)

			assertFalse(controller.isShuffling())

			val newState = PlayerState(
					Track(
							Artist("artist", "uri"),
							listOf(Artist("artist", "uri")),
							Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
					false, 1.0f, 200,
					PlayerOptions(true, Repeat.OFF),
					PlayerRestrictions(true, true, true, true, true, true)
			)
			spotifyCallback.lastValue.onEvent(newState)

			assertTrue(controller.isShuffling())
		}
	}

	@Test
	fun testToggleShuffle() {
		// test shuffle bool is flipped when shuffle is called: false -> true
		run {
			val state = PlayerState(
					Track(
							Artist("artist", "uri"),
							listOf(Artist("artist", "uri")),
							Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
					false, 1.0f, 200,
					PlayerOptions(false, Repeat.OFF),
					PlayerRestrictions(true, true, true, true, true, true)
			)
			spotifyCallback.lastValue.onEvent(state)

			controller.toggleShuffle()
			verify(playerApi).setShuffle(true)
		}

		// test shuffle bool is flipped when shuffle is called: true -> false
		run {
			val state = PlayerState(
					Track(
							Artist("artist", "uri"),
							listOf(Artist("artist", "uri")),
							Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
					false, 1.0f, 200,
					PlayerOptions(true, Repeat.OFF),
					PlayerRestrictions(true, true, true, true, true, true)
			)
			spotifyCallback.lastValue.onEvent(state)

			controller.toggleShuffle()
			verify(playerApi).setShuffle(false)
		}
	}

	@Test
	fun testRepeatMode() {
		// test repeat is off
		run {
			val state = PlayerState(
					Track(
							Artist("artist", "uri"),
							listOf(Artist("artist", "uri")),
							Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
					false, 1.0f, 200,
					PlayerOptions(false, Repeat.OFF),
					PlayerRestrictions(true, true, true, true, true, true)
			)
			spotifyCallback.lastValue.onEvent(state)

			assertEquals(RepeatMode.OFF, controller.getRepeatMode())
		}

		// test repeating all
		run {
			val state = PlayerState(
					Track(
							Artist("artist", "uri"),
							listOf(Artist("artist", "uri")),
							Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
					false, 1.0f, 200,
					PlayerOptions(false, Repeat.ALL),
					PlayerRestrictions(true, true, true, true, true, true)
			)
			spotifyCallback.lastValue.onEvent(state)

			assertEquals(RepeatMode.ALL, controller.getRepeatMode())
		}

		// test repeating one
		run {
			val state = PlayerState(
					Track(
							Artist("artist", "uri"),
							listOf(Artist("artist", "uri")),
							Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
					false, 1.0f, 200,
					PlayerOptions(false, Repeat.ONE),
					PlayerRestrictions(true, true, true, true, true, true)
			)
			spotifyCallback.lastValue.onEvent(state)

			assertEquals(RepeatMode.ONE, controller.getRepeatMode())
		}

		// test when switching to repeating all
		run {
			val state = PlayerState(
					Track(
							Artist("artist", "uri"),
							listOf(Artist("artist", "uri")),
							Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
					false, 1.0f, 200,
					PlayerOptions(false, Repeat.OFF),
					PlayerRestrictions(true, true, true, true, true, true)
			)
			spotifyCallback.lastValue.onEvent(state)

			assertEquals(RepeatMode.OFF, controller.getRepeatMode())

			val newState = PlayerState(
					Track(
							Artist("artist", "uri"),
							listOf(Artist("artist", "uri")),
							Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
					false, 1.0f, 200,
					PlayerOptions(false, Repeat.ALL),
					PlayerRestrictions(true, true, true, true, true, true)
			)
			spotifyCallback.lastValue.onEvent(newState)

			assertEquals(RepeatMode.ALL, controller.getRepeatMode())
		}
	}

	@Test
	fun testToggleRepeatFromOff_CannotRepeatTrackOrContext() {
		// repeat off -> repeat all, cannot repeat track, cannot repeat context
		val state = PlayerState(
				Track(
						Artist("artist", "uri"),
						listOf(Artist("artist", "uri")),
						Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
				false, 1.0f, 200,
				PlayerOptions(false, Repeat.OFF),
				PlayerRestrictions(true, true, false, false, true, true)
		)
		spotifyCallback.lastValue.onEvent(state)
		controller.toggleRepeat()

		verify(playerApi, never()).setRepeat(any())
	}

	@Test
	fun testToggleRepeatFromOff_CanRepeatTrackAndContext() {
		// repeat off -> repeat all, can repeat track, can repeat context
		val state = PlayerState(
				Track(
						Artist("artist", "uri"),
						listOf(Artist("artist", "uri")),
						Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
				false, 1.0f, 200,
				PlayerOptions(false, Repeat.OFF),
				PlayerRestrictions(true, true, true, true, true, true)
		)
		spotifyCallback.lastValue.onEvent(state)
		controller.toggleRepeat()

		verify(playerApi).setRepeat(Repeat.ALL)
	}

	@Test
	fun testToggleRepeatFromOff_CanRepeatTrackCannotRepeatContext() {
		// repeat off -> repeat all, can repeat track, cannot repeat context
		val state = PlayerState(
				Track(
						Artist("artist", "uri"),
						listOf(Artist("artist", "uri")),
						Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
				false, 1.0f, 200,
				PlayerOptions(false, Repeat.OFF),
				PlayerRestrictions(true, true, true, false, true, true)
		)
		spotifyCallback.lastValue.onEvent(state)
		controller.toggleRepeat()

		verify(playerApi).setRepeat(Repeat.ONE)
	}

	@Test
	fun testToggleRepeatFromAll_CanRepeatTrackAndContext() {
		// repeat all -> repeat one, can repeat track, can repeat context
		val state = PlayerState(
				Track(
						Artist("artist", "uri"),
						listOf(Artist("artist", "uri")),
						Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
				false, 1.0f, 200,
				PlayerOptions(false, Repeat.ALL),
				PlayerRestrictions(true, true, true, true, true, true)
		)
		spotifyCallback.lastValue.onEvent(state)
		controller.toggleRepeat()

		verify(playerApi).setRepeat(Repeat.ONE)
	}

	@Test
	fun testToggleRepeatFromAll_CannotRepeatTrackCanRepeatContext() {
		// repeat all -> repeat one, cannot repeat track, can repeat context
		val state = PlayerState(
				Track(
						Artist("artist", "uri"),
						listOf(Artist("artist", "uri")),
						Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
				false, 1.0f, 200,
				PlayerOptions(false, Repeat.ALL),
				PlayerRestrictions(true, true, false, true, true, true)
		)
		spotifyCallback.lastValue.onEvent(state)
		controller.toggleRepeat()

		verify(playerApi).setRepeat(Repeat.OFF)
	}

	@Test
	fun testToggleRepeatFromOne() {
		// repeat one -> repeat off
		val state = PlayerState(
				Track(
						Artist("artist", "uri"),
						listOf(Artist("artist", "uri")),
						Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
				false, 1.0f, 200,
				PlayerOptions(false, Repeat.ONE),
				PlayerRestrictions(true, true, true, true, true, true)
		)
		spotifyCallback.lastValue.onEvent(state)
		controller.toggleRepeat()

		verify(playerApi).setRepeat(Repeat.OFF)
	}

	@Test
	fun testGetCustomActions() {
		// shuffle off, repeat off, can repeat playlist and track
		run {
			val state = PlayerState(
					Track(
							Artist("artist", "uri"),
							listOf(Artist("artist", "uri")),
							Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
					false, 1.0f, 200,
					PlayerOptions(false, Repeat.OFF),
					PlayerRestrictions(true, true, true, true, true, true)
			)
			spotifyCallback.lastValue.onEvent(state)

			assertEquals(setOf(
					controller.CUSTOM_ACTION_TURN_REPEAT_ALL_ON
			), controller.getCustomActions().toSet())

			libraryCallback.lastValue.onResult(LibraryState("uri", false, true))
			assertEquals(setOf(
					controller.CUSTOM_ACTION_TURN_REPEAT_ALL_ON,
					controller.CUSTOM_ACTION_ADD_TO_COLLECTION
			), controller.getCustomActions().toSet())

			libraryCallback.lastValue.onResult(LibraryState("uri", true, true))
			assertEquals(setOf(
					controller.CUSTOM_ACTION_TURN_REPEAT_ALL_ON,
					controller.CUSTOM_ACTION_REMOVE_FROM_COLLECTION
			), controller.getCustomActions().toSet())
		}

		// shuffle off, repeat off, can repeat track but not playlist
		run {
			val state = PlayerState(
					Track(
							Artist("artist", "uri"),
							listOf(Artist("artist", "uri")),
							Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
					false, 1.0f, 200,
					PlayerOptions(false, Repeat.OFF),
					PlayerRestrictions(true, true, true, false, true, true)
			)
			spotifyCallback.lastValue.onEvent(state)

			assertEquals(setOf(
					controller.CUSTOM_ACTION_TURN_REPEAT_ONE_ON
			), controller.getCustomActions().toSet())
		}

		// shuffle off, repeat off, can not repeat
		run {
			val state = PlayerState(
					Track(
							Artist("artist", "uri"),
							listOf(Artist("artist", "uri")),
							Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
					false, 1.0f, 200,
					PlayerOptions(false, Repeat.OFF),
					PlayerRestrictions(true, true, false, false, true, true)
			)
			spotifyCallback.lastValue.onEvent(state)

			assertEquals(emptySet<CustomAction>(), controller.getCustomActions().toSet())
		}

		// shuffle on, repeat all, can repeat playlist and track
		run {
			val state = PlayerState(
					Track(
							Artist("artist", "uri"),
							listOf(Artist("artist", "uri")),
							Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
					false, 1.0f, 200,
					PlayerOptions(true, Repeat.ALL),
					PlayerRestrictions(true, true, true, true, true, true)
			)
			spotifyCallback.lastValue.onEvent(state)

			assertEquals(setOf(
					controller.CUSTOM_ACTION_TURN_REPEAT_ONE_ON
			), controller.getCustomActions().toSet())
		}

		// shuffle on, repeat all, can repeat playlist but not track
		run {
			val state = PlayerState(
					Track(
							Artist("artist", "uri"),
							listOf(Artist("artist", "uri")),
							Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
					false, 1.0f, 200,
					PlayerOptions(true, Repeat.ALL),
					PlayerRestrictions(true, true, false, true, true, true)
			)
			spotifyCallback.lastValue.onEvent(state)

			assertEquals(setOf(
					controller.CUSTOM_ACTION_TURN_REPEAT_ONE_OFF
			), controller.getCustomActions().toSet())
		}

		// shuffle on, repeat one, can repeat playlist and track
		run {
			val state = PlayerState(
					Track(
							Artist("artist", "uri"),
							listOf(Artist("artist", "uri")),
							Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
					false, 1.0f, 200,
					PlayerOptions(true, Repeat.ONE),
					PlayerRestrictions(true, true, true, true, true, true)
			)
			spotifyCallback.lastValue.onEvent(state)

			assertEquals(setOf(
					controller.CUSTOM_ACTION_TURN_REPEAT_ONE_OFF
			), controller.getCustomActions().toSet())
		}
	}

	@Test
	fun testGetCoverArt() {
		val imageUri = ImageUri("test")
		val mockBitmap: Bitmap = mock()

		// cover art cache miss
		val nullCoverArtBitmap = controller.getCoverArt(imageUri)
		assertNull(nullCoverArtBitmap)

		verify(imagesApi).getImage(imageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(mockBitmap)

		// cover art cache hit
		val coverArtBitmap = controller.getCoverArt(imageUri)
		assertEquals(mockBitmap, coverArtBitmap)
	}

	@Test
	fun testQueue() {
		val playerContext = PlayerContext("playlistUri", "title", "subtitle", "playlist")
		val queueImageUri = ImageUri("imageUri")
		val queueCoverArtBitmap: Bitmap = mock()

		// load a queue
		playlistCallback.lastValue.onEvent(playerContext)
		verify(webApi).clearPendingQueueMetadataCreate()
		verify(contentApi).getChildrenOfItem(ListItem(playerContext.uri, playerContext.uri, null, playerContext.title, playerContext.subtitle, false, true), 200, 0)
		contentCallback.lastValue.onResult(ListItems(200, 0, 2, arrayOf(
				ListItem("id", "uri", null, "Title", "Subtitle", true, false)
		)))

		// it should request again
		verify(contentApi).getChildrenOfItem(ListItem(playerContext.uri, playerContext.uri, null, playerContext.title, playerContext.subtitle, false, true), 200, 1)
		contentCallback.lastValue.onResult(ListItems(200, 1, 2, arrayOf(
				ListItem("id2", "uri2", null, "Title2", "Subtitle", true, false)
		)))

		// it should get the QueueMetadata information
		val recentlyPlayedUri = "com.spotify.recently-played"
		verify(contentApi).getChildrenOfItem(ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true), 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(
				ListItem("queueId", "queueUri", queueImageUri, playerContext.title, playerContext.subtitle, false, true)
		)))

		verify(imagesApi).getImage(queueImageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(queueCoverArtBitmap)

		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(playerContext.title, queue!!.title)
		assertEquals(playerContext.subtitle, queue.subtitle)
		assertEquals(queueCoverArtBitmap, queue.coverArt)
		assertNotNull(queue.songs)

		val songs = queue.songs!!
		assertEquals(2, songs.size)
		assertNotEquals(null, songs[0].queueId)
		assertEquals("Title", songs[0].title)
		assertEquals("Title2", songs[1].title)

		// fail to skip
		controller.playQueue(MusicMetadata(queueId = 345))
		verify(playerApi, never()).skipToIndex(any(), any())

		// try to skip to it
		controller.playQueue(songs[0])
		verify(playerApi).skipToIndex(playerContext.uri, 0)
	}

	@Test
	fun testQueue_Playlist_WebAPILoaded() = runBlockingTest {
		val playerContext = PlayerContext("playlistUri", "title", "subtitle", "playlist")
		val queueImageUri = ImageUri("imageUri")
		val queueCoverArtBitmap: Bitmap = mock()

		val playlistSongs = listOf(
				SpotifyMusicMetadata(controller, "mediaId1", 1, "coverArtUri1", "Artist 1", "Album 1", "Title 1"),
				SpotifyMusicMetadata(controller, "mediaId2", 2, "coverArtUri2", "Artist 2", "Album 2", "Title 2")
		)

		whenever(webApi.getPlaylistSongs(controller, playerContext.uri)).thenReturn(playlistSongs)

		// load a queue
		playlistCallback.lastValue.onEvent(playerContext)
		verify(webApi).clearPendingQueueMetadataCreate()
		verify(contentApi, never()).getChildrenOfItem(ListItem(playerContext.uri, playerContext.uri, null, playerContext.title, playerContext.subtitle, false, true), 200, 0)

		// it should get the QueueMetadata information
		val recentlyPlayedUri = "com.spotify.recently-played"
		verify(contentApi).getChildrenOfItem(ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true), 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(
				ListItem("queueId", "queueUri", queueImageUri, playerContext.title, playerContext.subtitle, false, true)
		)))

		verify(imagesApi).getImage(queueImageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(queueCoverArtBitmap)

		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(playerContext.title, queue!!.title)
		assertEquals(playerContext.subtitle, queue.subtitle)
		assertEquals(queueCoverArtBitmap, queue.coverArt)
		assertNotNull(queue.songs)

		val songs = queue.songs!!
		assertEquals(2, songs.size)
		assertEquals("mediaId1", songs[0].mediaId)
		assertEquals("Title 1", songs[0].title)
		assertEquals("mediaId2", songs[1].mediaId)
		assertEquals("Title 2", songs[1].title)
	}

	@Test
	fun testQueue_Playlist_WebAPINotLoaded() = runBlockingTest {
		val playerContext = PlayerContext("playlistUri", "title", "subtitle", "playlist")
		val queueImageUri = ImageUri("imageUri")
		val queueCoverArtBitmap: Bitmap = mock()

		whenever(webApi.getPlaylistSongs(controller, playerContext.uri)).thenReturn(emptyList())

		// load a queue
		playlistCallback.lastValue.onEvent(playerContext)
		verify(webApi).clearPendingQueueMetadataCreate()
		verify(webApi).getPlaylistSongs(controller, playerContext.uri)
		verify(contentApi).getChildrenOfItem(ListItem(playerContext.uri, playerContext.uri, null, playerContext.title, playerContext.subtitle, false, true), 200, 0)
		contentCallback.lastValue.onResult(ListItems(200, 0, 2, arrayOf(
				ListItem("mediaId1", "mediaId1", null, "Title 1", "Subtitle 1", true, false),
				ListItem("mediaId2", "mediaId2", null, "Title 2", "Subtitle 2", true, false)
		)))

		// it should get the QueueMetadata information
		val recentlyPlayedUri = "com.spotify.recently-played"
		verify(contentApi).getChildrenOfItem(ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true), 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(
				ListItem("queueId", "queueUri", queueImageUri, playerContext.title, playerContext.subtitle, false, true)
		)))

		verify(imagesApi).getImage(queueImageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(queueCoverArtBitmap)

		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(playerContext.title, queue!!.title)
		assertEquals(playerContext.subtitle, queue.subtitle)
		assertEquals(queueCoverArtBitmap, queue.coverArt)
		assertNotNull(queue.songs)

		val songs = queue.songs!!
		assertEquals(2, songs.size)
		assertEquals("mediaId1", songs[0].mediaId)
		assertEquals("Title 1", songs[0].title)
		assertEquals("mediaId2", songs[1].mediaId)
		assertEquals("Title 2", songs[1].title)
	}

	@Test
	fun testQueue_LikedSongsPlaylist_WebAPILoaded_NoCachedContent_NoExistingPlaylist() = runBlockingTest {
		val playerContext = PlayerContext("uri", "Liked Songs", null, "your_library_tracks")
		val queueImageUriStr = "imageUri"
		val queueImageUri = ImageUri(queueImageUriStr)
		val queueCoverArtBitmap: Bitmap = mock()
		val queueCoverArtBase64 = Base64.encodeToString(Utils.compressBitmapJpg(queueCoverArtBitmap, 85), Base64.NO_WRAP)

		val playlistUriStr = "playlistUri"
		val playlistId = "playlistId"
		val playlistUri: PlaylistUri = mock()
		whenever(playlistUri.id) doAnswer { playlistId }
		whenever(playlistUri.uri) doAnswer { playlistUriStr }

		val likedSongs = listOf(
				SpotifyMusicMetadata(controller, "mediaId1", 1, "coverArtUri1", "Artist 1", "Album 1", "Title 1"),
				SpotifyMusicMetadata(controller, "mediaId2", 2, "coverArtUri2", "Artist 2", "Album 2", "Title 2")
		)
		whenever(webApi.getLikedSongs(controller)) doAnswer { likedSongs }
		whenever(webApi.getPlaylistUri(SpotifyWebApi.LIKED_SONGS_PLAYLIST_NAME)) doAnswer { null }
		whenever(webApi.createPlaylist(SpotifyWebApi.LIKED_SONGS_PLAYLIST_NAME, L.MUSIC_TEMPORARY_PLAYLIST_DESCRIPTION)) doAnswer { playlistUri }
		whenever(webApi.addSongsToPlaylist(playlistId, likedSongs)) doAnswer { }
		whenever(webApi.setPlaylistImage(playlistId, queueCoverArtBase64)) doAnswer { }

		playlistCallback.lastValue.onEvent(playerContext)

		verify(webApi).addSongsToPlaylist(playlistId, likedSongs)

		assertEquals(controller.currentPlayerContext.uri, playlistUriStr)

		// it should get the QueueMetadata information
		val recentlyPlayedUri = "com.spotify.recently-played"
		verify(contentApi).getChildrenOfItem(ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true), 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(
				ListItem("queueId", "queueUri", queueImageUri, playerContext.title, playerContext.subtitle, false, true)
		)))

		verify(imagesApi).getImage(queueImageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(queueCoverArtBitmap)

		val expectedQueueMetadata = QueueMetadata(playerContext.title, playerContext.subtitle, likedSongs, queueCoverArtBitmap, playlistUriStr)
		assertEquals(controller.queueMetadata, expectedQueueMetadata)

		verify(webApi).setPlaylistImage(playlistId, queueCoverArtBase64)

		val likedSongsPlaylistState = TemporaryPlaylistState(likedSongs.hashCode().toString(), playlistUriStr, playlistId, playerContext.title, playerContext.uri)
		assertEquals(appSettings[AppSettings.KEYS.SPOTIFY_LIKED_SONGS_PLAYLIST_STATE], gson.toJson(likedSongsPlaylistState))

		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(playerContext.title, queue!!.title)
		assertEquals(playerContext.subtitle, queue.subtitle)
		assertEquals(queueCoverArtBitmap, queue.coverArt)
		assertNotNull(queue.songs)

		val songs = queue.songs!!
		assertEquals(2, songs.size)
		assertEquals("mediaId1", songs[0].mediaId)
		assertEquals("Title 1", songs[0].title)
		assertEquals("mediaId2", songs[1].mediaId)
		assertEquals("Title 2", songs[1].title)
	}

	@Test
	fun testQueue_LikedSongsPlaylist_WebAPILoaded_NoCachedContent_ExistingPlaylist() = runBlockingTest {
		val playerContext = PlayerContext("uri", "Liked Songs", null, "your_library_tracks")
		val queueImageUriStr = "imageUri"
		val queueImageUri = ImageUri(queueImageUriStr)
		val queueCoverArtBitmap: Bitmap = mock()
		val queueCoverArtBase64 = Base64.encodeToString(Utils.compressBitmapJpg(queueCoverArtBitmap, 85), Base64.NO_WRAP)

		val playlistUriStr = "playlistUri"
		val playlistId = "playlistId"
		val playlistUri: SpotifyUri = mock()
		whenever(playlistUri.id) doAnswer { playlistId }
		whenever(playlistUri.uri) doAnswer { playlistUriStr }

		val likedSongs = listOf(
				SpotifyMusicMetadata(controller, "mediaId1", 1, "coverArtUri1", "Artist 1", "Album 1", "Title 1"),
				SpotifyMusicMetadata(controller, "mediaId2", 2, "coverArtUri2", "Artist 2", "Album 2", "Title 2")
		)
		whenever(webApi.getLikedSongs(controller)) doAnswer { likedSongs }
		whenever(webApi.getPlaylistUri(SpotifyWebApi.LIKED_SONGS_PLAYLIST_NAME)) doAnswer { playlistUri }
		whenever(webApi.setPlaylistImage(playlistId, queueCoverArtBase64)) doAnswer { }

		verify(webApi, never()).createPlaylist(SpotifyWebApi.LIKED_SONGS_PLAYLIST_NAME)
		verify(webApi, never()).addSongsToPlaylist(playlistId, likedSongs)

		playlistCallback.lastValue.onEvent(playerContext)

		assertEquals(controller.currentPlayerContext.uri, playlistUriStr)

		// it should get the QueueMetadata information
		val recentlyPlayedUri = "com.spotify.recently-played"
		verify(contentApi).getChildrenOfItem(ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true), 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(
				ListItem("queueId", "queueUri", queueImageUri, playerContext.title, playerContext.subtitle, false, true)
		)))

		verify(imagesApi).getImage(queueImageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(queueCoverArtBitmap)

		val expectedQueueMetadata = QueueMetadata(playerContext.title, playerContext.subtitle, likedSongs, queueCoverArtBitmap, playlistUriStr)
		assertEquals(controller.queueMetadata, expectedQueueMetadata)

		verify(webApi).setPlaylistImage(playlistId, queueCoverArtBase64)

		val likedSongsPlaylistState = TemporaryPlaylistState(likedSongs.hashCode().toString(), playlistUriStr, playlistId, playerContext.title, playerContext.uri)
		assertEquals(appSettings[AppSettings.KEYS.SPOTIFY_LIKED_SONGS_PLAYLIST_STATE], gson.toJson(likedSongsPlaylistState))

		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(playerContext.title, queue!!.title)
		assertEquals(playerContext.subtitle, queue.subtitle)
		assertEquals(queueCoverArtBitmap, queue.coverArt)
		assertNotNull(queue.songs)

		val songs = queue.songs!!
		assertEquals(2, songs.size)
		assertEquals("mediaId1", songs[0].mediaId)
		assertEquals("Title 1", songs[0].title)
		assertEquals("mediaId2", songs[1].mediaId)
		assertEquals("Title 2", songs[1].title)
	}

	@Test
	fun testQueue_LikedSongsPlaylist_WebAPILoaded_NoCachedContent_PlaylistCreationFailure() = runBlockingTest {
		val playerContext = PlayerContext("playlistUri", "Liked Songs", null, "your_library_tracks")
		val queueImageUri = ImageUri("imageUri")
		val queueCoverArtBitmap: Bitmap = mock()

		val likedSongs = listOf(
				SpotifyMusicMetadata(controller, "mediaId1", 1, "coverArtUri1", "Artist 1", "Album 1", "Title 1"),
				SpotifyMusicMetadata(controller, "mediaId2", 2, "coverArtUri2", "Artist 2", "Album 2", "Title 2")
		)
		whenever(webApi.getLikedSongs(controller)) doAnswer { likedSongs }
		whenever(webApi.createPlaylist(SpotifyWebApi.LIKED_SONGS_PLAYLIST_NAME, L.MUSIC_TEMPORARY_PLAYLIST_DESCRIPTION)) doAnswer { null }

		playlistCallback.lastValue.onEvent(playerContext)

		verify(webApi, never()).addSongsToPlaylist(any(), any())

		verify(contentApi).getChildrenOfItem(ListItem(playerContext.uri, playerContext.uri, null, playerContext.title, playerContext.subtitle, false, true), 200, 0)
		contentCallback.lastValue.onResult(ListItems(200, 0, 2, arrayOf(
				ListItem("mediaId1", "mediaId1", null, "Title 1", "Subtitle 1", true, false),
				ListItem("mediaId2", "mediaId2", null, "Title 2", "Subtitle 2", true, false)
		)))

		// it should get the QueueMetadata information when called through the Spotify App Remote
		val recentlyPlayedUri = "com.spotify.recently-played"
		verify(contentApi).getChildrenOfItem(ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true), 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(
				ListItem("queueId", "queueUri", queueImageUri, playerContext.title, playerContext.subtitle, false, true)
		)))

		verify(imagesApi).getImage(queueImageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(queueCoverArtBitmap)

		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(playerContext.title, queue!!.title)
		assertEquals(playerContext.subtitle, queue.subtitle)
		assertEquals(queueCoverArtBitmap, queue.coverArt)
		assertNotNull(queue.songs)

		val songs = queue.songs!!
		assertEquals(2, songs.size)
		assertEquals("mediaId1", songs[0].mediaId)
		assertEquals("Title 1", songs[0].title)
		assertEquals("mediaId2", songs[1].mediaId)
		assertEquals("Title 2", songs[1].title)
	}

	@Test
	fun testQueue_LikedSongsPlaylist_WebAPILoaded_CachedContent_PlaylistDataInvalid() = runBlockingTest {
		val playerContext = PlayerContext("playlistUri", "Liked Songs", null, "your_library_tracks")
		val queueImageUriStr = "imageUri"
		val queueImageUri = ImageUri(queueImageUriStr)
		val queueCoverArtBitmap: Bitmap = mock()

		val playlistId = "playlistId"

		val likedSongs = listOf(
				SpotifyMusicMetadata(controller, "mediaId1", 1, "coverArtUri1", "Artist 1", "Album 1", "Title 1"),
				SpotifyMusicMetadata(controller, "mediaId2", 2, "coverArtUri2", "Artist 2", "Album 2", "Title 2")
		)
		whenever(webApi.getLikedSongs(controller)) doAnswer { likedSongs }
		whenever(webApi.replacePlaylistSongs(playlistId, likedSongs)) doAnswer { }

		val likedSongsState = TemporaryPlaylistState("invalidHashCode", playerContext.uri, playlistId, playerContext.title, playerContext.uri)
		appSettings[AppSettings.KEYS.SPOTIFY_LIKED_SONGS_PLAYLIST_STATE] = gson.toJson(likedSongsState)

		playlistCallback.lastValue.onEvent(playerContext)

		verify(webApi, never()).addSongsToPlaylist(playlistId, likedSongs)
		verify(webApi, never()).createPlaylist(SpotifyWebApi.LIKED_SONGS_PLAYLIST_NAME, L.MUSIC_TEMPORARY_PLAYLIST_DESCRIPTION)
		verify(webApi).replacePlaylistSongs(playlistId, likedSongs)

		assertEquals(controller.currentPlayerContext.uri, playerContext.uri)

		// it should get the QueueMetadata information
		val recentlyPlayedUri = "com.spotify.recently-played"
		verify(contentApi).getChildrenOfItem(ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true), 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(
				ListItem("queueId", "queueUri", queueImageUri, playerContext.title, playerContext.subtitle, false, true)
		)))

		verify(imagesApi).getImage(queueImageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(queueCoverArtBitmap)

		val expectedQueueMetadata = QueueMetadata(playerContext.title, playerContext.subtitle, likedSongs, queueCoverArtBitmap, playerContext.uri)
		assertEquals(controller.queueMetadata, expectedQueueMetadata)

		val likedSongsPlaylistState = TemporaryPlaylistState(likedSongs.hashCode().toString(), playerContext.uri, playlistId, playerContext.title, playerContext.uri)
		assertEquals(appSettings[AppSettings.KEYS.SPOTIFY_LIKED_SONGS_PLAYLIST_STATE], gson.toJson(likedSongsPlaylistState))

		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(playerContext.title, queue!!.title)
		assertEquals(playerContext.subtitle, queue.subtitle)
		assertEquals(queueCoverArtBitmap, queue.coverArt)
		assertNotNull(queue.songs)

		val songs = queue.songs!!
		assertEquals(2, songs.size)
		assertEquals("mediaId1", songs[0].mediaId)
		assertEquals("Title 1", songs[0].title)
		assertEquals("mediaId2", songs[1].mediaId)
		assertEquals("Title 2", songs[1].title)
	}

	@Test
	fun testQueue_LikedSongsPlaylist_WebAPINotLoaded() = runBlockingTest {
		val playerContext = PlayerContext("playlistUri", "Liked Songs", null, "your_library_tracks")
		val queueImageUri = ImageUri("imageUri")
		val queueCoverArtBitmap: Bitmap = mock()

		whenever(webApi.getLikedSongs(controller)) doAnswer { emptyList() }

		playlistCallback.lastValue.onEvent(playerContext)

		verify(webApi, never()).getPlaylistSongs(controller, playerContext.uri)
		verify(contentApi).getChildrenOfItem(ListItem(playerContext.uri, playerContext.uri, null, playerContext.title, playerContext.subtitle, false, true), 200, 0)
		contentCallback.lastValue.onResult(ListItems(200, 0, 2, arrayOf(
				ListItem("mediaId1", "mediaId1", null, "Title 1", "Subtitle 1", true, false),
				ListItem("mediaId2", "mediaId2", null, "Title 2", "Subtitle 2", true, false)
		)))

		// it should get the QueueMetadata information when called through the Spotify App Remote
		val recentlyPlayedUri = "com.spotify.recently-played"
		verify(contentApi).getChildrenOfItem(ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true), 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(
				ListItem("queueId", "queueUri", queueImageUri, playerContext.title, playerContext.subtitle, false, true)
		)))

		verify(imagesApi).getImage(queueImageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(queueCoverArtBitmap)

		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(playerContext.title, queue!!.title)
		assertEquals(playerContext.subtitle, queue.subtitle)
		assertEquals(queueCoverArtBitmap, queue.coverArt)
		assertNotNull(queue.songs)

		val songs = queue.songs!!
		assertEquals(2, songs.size)
		assertEquals("mediaId1", songs[0].mediaId)
		assertEquals("Title 1", songs[0].title)
		assertEquals("mediaId2", songs[1].mediaId)
		assertEquals("Title 2", songs[1].title)
	}

	@Test
	fun testQueue_ArtistPlaylist_WebAPILoaded_NoCachedContent_NoExistingPlaylist() = runBlockingTest {
		val playerContext = PlayerContext("artistUri", "Artist", null, "artist")
		val queueImageUriStr = "imageUri"
		val queueImageUri = ImageUri(queueImageUriStr)
		val queueCoverArtBitmap: Bitmap = mock()
		val queueCoverArtBase64 = Base64.encodeToString(Utils.compressBitmapJpg(queueCoverArtBitmap, 85), Base64.NO_WRAP)

		val playlistUriStr = "playlistUri"
		val playlistId = "playlistId"
		val playlistUri: PlaylistUri = mock()
		whenever(playlistUri.id) doAnswer { playlistId }
		whenever(playlistUri.uri) doAnswer { playlistUriStr }

		val artistSongs = listOf(
				SpotifyMusicMetadata(controller, "mediaId1", 1, "coverArtUri1", "Artist 1", "Album 1", "Title 1"),
				SpotifyMusicMetadata(controller, "mediaId2", 2, "coverArtUri2", "Artist 2", "Album 2", "Title 2")
		)
		whenever(webApi.getArtistTopSongs(controller, playerContext.uri)) doAnswer { artistSongs }
		whenever(webApi.getPlaylistUri(SpotifyWebApi.ARTIST_SONGS_PLAYLIST_NAME)) doAnswer { null }
		whenever(webApi.createPlaylist(SpotifyWebApi.ARTIST_SONGS_PLAYLIST_NAME, L.MUSIC_TEMPORARY_PLAYLIST_DESCRIPTION)) doAnswer { playlistUri }
		whenever(webApi.addSongsToPlaylist(playlistId, artistSongs)) doAnswer { }
		whenever(webApi.setPlaylistImage(playlistId, queueCoverArtBase64)) doAnswer { }

		playlistCallback.lastValue.onEvent(playerContext)

		verify(webApi).addSongsToPlaylist(playlistId, artistSongs)

		assertEquals(controller.currentPlayerContext.uri, playlistUriStr)

		val recentlyPlayedUri = "com.spotify.recently-played"
		verify(contentApi).getChildrenOfItem(ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true), 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(
				ListItem("queueId", "queueUri", queueImageUri, playerContext.title, playerContext.subtitle, false, true)
		)))

		verify(imagesApi).getImage(queueImageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(queueCoverArtBitmap)

		val expectedQueueMetadata = QueueMetadata(playerContext.title, playerContext.subtitle, artistSongs, queueCoverArtBitmap, playlistUriStr)
		assertEquals(controller.queueMetadata, expectedQueueMetadata)

		verify(webApi).setPlaylistImage(playlistId, queueCoverArtBase64)

		val artistSongsPlaylistState = TemporaryPlaylistState(artistSongs.hashCode().toString(), playlistUriStr, playlistId, playerContext.title, playerContext.uri)
		assertEquals(appSettings[AppSettings.KEYS.SPOTIFY_ARTIST_SONGS_PLAYLIST_STATE], gson.toJson(artistSongsPlaylistState))

		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(playerContext.title, queue!!.title)
		assertEquals(playerContext.subtitle, queue.subtitle)
		assertEquals(queueCoverArtBitmap, queue.coverArt)
		assertNotNull(queue.songs)

		val songs = queue.songs!!
		assertEquals(2, songs.size)
		assertEquals("mediaId1", songs[0].mediaId)
		assertEquals("Title 1", songs[0].title)
		assertEquals("mediaId2", songs[1].mediaId)
		assertEquals("Title 2", songs[1].title)
	}

	@Test
	fun testQueue_ArtistPlaylist_WebAPILoaded_ArtistTitleBlank_NoCachedContent_NoExistingPlaylist() = runBlockingTest {
		val artistTitle = "Artist"
		val playerContext = PlayerContext("artistUri", "", null, "artist")
		val queueImageUriStr = "imageUri"
		val queueImageUri = ImageUri(queueImageUriStr)
		val queueCoverArtBitmap: Bitmap = mock()
		val queueCoverArtBase64 = Base64.encodeToString(Utils.compressBitmapJpg(queueCoverArtBitmap, 85), Base64.NO_WRAP)

		val playlistUriStr = "playlistUri"
		val playlistId = "playlistId"
		val playlistUri: PlaylistUri = mock()
		whenever(playlistUri.id) doAnswer { playlistId }
		whenever(playlistUri.uri) doAnswer { playlistUriStr }

		val artistSongs = listOf(
				SpotifyMusicMetadata(controller, "mediaId1", 1, "coverArtUri1", "Artist 1", "Album 1", "Title 1"),
				SpotifyMusicMetadata(controller, "mediaId2", 2, "coverArtUri2", "Artist 2", "Album 2", "Title 2")
		)
		whenever(webApi.getArtistTopSongs(controller, playerContext.uri)) doAnswer { artistSongs }
		whenever(webApi.getPlaylistUri(SpotifyWebApi.ARTIST_SONGS_PLAYLIST_NAME)) doAnswer { null }
		whenever(webApi.createPlaylist(SpotifyWebApi.ARTIST_SONGS_PLAYLIST_NAME, L.MUSIC_TEMPORARY_PLAYLIST_DESCRIPTION)) doAnswer { playlistUri }
		whenever(webApi.addSongsToPlaylist(playlistId, artistSongs)) doAnswer { }
		whenever(webApi.setPlaylistImage(playlistId, queueCoverArtBase64)) doAnswer { }

		playlistCallback.lastValue.onEvent(playerContext)

		val recentlyPlayedUri = "com.spotify.recently-played"
		val recentlyPlayedRequestListItem = ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true)
		val recentlyPlayedListItemResponse = ListItem("queueId", "queueUri", queueImageUri, artistTitle, playerContext.subtitle, false, true)
		verify(contentApi).getChildrenOfItem(recentlyPlayedRequestListItem, 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(recentlyPlayedListItemResponse)))

		assertEquals(controller.currentPlayerContext.title, artistTitle)

		verify(webApi).addSongsToPlaylist(playlistId, artistSongs)

		assertEquals(controller.currentPlayerContext.uri, playlistUriStr)

		verify(contentApi, times(2)).getChildrenOfItem(recentlyPlayedRequestListItem, 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(recentlyPlayedListItemResponse)))

		verify(imagesApi).getImage(queueImageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(queueCoverArtBitmap)

		val expectedQueueMetadata = QueueMetadata(artistTitle, playerContext.subtitle, artistSongs, queueCoverArtBitmap, playlistUriStr)
		assertEquals(controller.queueMetadata, expectedQueueMetadata)

		verify(webApi).setPlaylistImage(playlistId, queueCoverArtBase64)

		val artistSongsPlaylistState = TemporaryPlaylistState(artistSongs.hashCode().toString(), playlistUriStr, playlistId, artistTitle, playerContext.uri)
		assertEquals(appSettings[AppSettings.KEYS.SPOTIFY_ARTIST_SONGS_PLAYLIST_STATE], gson.toJson(artistSongsPlaylistState))

		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(artistTitle, queue!!.title)
		assertEquals(playerContext.subtitle, queue.subtitle)
		assertEquals(queueCoverArtBitmap, queue.coverArt)
		assertNotNull(queue.songs)

		val songs = queue.songs!!
		assertEquals(2, songs.size)
		assertEquals("mediaId1", songs[0].mediaId)
		assertEquals("Title 1", songs[0].title)
		assertEquals("mediaId2", songs[1].mediaId)
		assertEquals("Title 2", songs[1].title)
	}

	@Test
	fun testQueue_ArtistPlaylist_WebAPILoaded_NoCachedContent_ExistingPlaylist() = runBlockingTest {
		val playerContext = PlayerContext("artistUri", "Artist", null, "artist")
		val queueImageUriStr = "imageUri"
		val queueImageUri = ImageUri(queueImageUriStr)
		val queueCoverArtBitmap: Bitmap = mock()
		val queueCoverArtBase64 = Base64.encodeToString(Utils.compressBitmapJpg(queueCoverArtBitmap, 85), Base64.NO_WRAP)

		val playlistUriStr = "playlistUri"
		val playlistId = "playlistId"
		val playlistUri: PlaylistUri = mock()
		whenever(playlistUri.id) doAnswer { playlistId }
		whenever(playlistUri.uri) doAnswer { playlistUriStr }

		val artistSongs = listOf(
				SpotifyMusicMetadata(controller, "mediaId1", 1, "coverArtUri1", "Artist 1", "Album 1", "Title 1"),
				SpotifyMusicMetadata(controller, "mediaId2", 2, "coverArtUri2", "Artist 2", "Album 2", "Title 2")
		)
		whenever(webApi.getArtistTopSongs(controller, playerContext.uri)) doAnswer { artistSongs }
		whenever(webApi.getPlaylistUri(SpotifyWebApi.ARTIST_SONGS_PLAYLIST_NAME)) doAnswer { playlistUri }
		whenever(webApi.setPlaylistImage(playlistId, queueCoverArtBase64)) doAnswer { }

		playlistCallback.lastValue.onEvent(playerContext)

		verify(webApi, never()).createPlaylist(SpotifyWebApi.ARTIST_SONGS_PLAYLIST_NAME, L.MUSIC_TEMPORARY_PLAYLIST_DESCRIPTION)
		verify(webApi, never()).addSongsToPlaylist(playlistId, artistSongs)

		assertEquals(controller.currentPlayerContext.uri, playlistUriStr)

		val recentlyPlayedUri = "com.spotify.recently-played"
		verify(contentApi).getChildrenOfItem(ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true), 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(
				ListItem("queueId", "queueUri", queueImageUri, playerContext.title, playerContext.subtitle, false, true)
		)))

		verify(imagesApi).getImage(queueImageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(queueCoverArtBitmap)

		val expectedQueueMetadata = QueueMetadata(playerContext.title, playerContext.subtitle, artistSongs, queueCoverArtBitmap, playlistUriStr)
		assertEquals(controller.queueMetadata, expectedQueueMetadata)

		verify(webApi).setPlaylistImage(playlistId, queueCoverArtBase64)

		val artistSongsPlaylistState = TemporaryPlaylistState(artistSongs.hashCode().toString(), playlistUriStr, playlistId, playerContext.title, playerContext.uri)
		assertEquals(appSettings[AppSettings.KEYS.SPOTIFY_ARTIST_SONGS_PLAYLIST_STATE], gson.toJson(artistSongsPlaylistState))

		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(playerContext.title, queue!!.title)
		assertEquals(playerContext.subtitle, queue.subtitle)
		assertEquals(queueCoverArtBitmap, queue.coverArt)
		assertNotNull(queue.songs)

		val songs = queue.songs!!
		assertEquals(2, songs.size)
		assertEquals("mediaId1", songs[0].mediaId)
		assertEquals("Title 1", songs[0].title)
		assertEquals("mediaId2", songs[1].mediaId)
		assertEquals("Title 2", songs[1].title)
	}

	@Test
	fun testQueue_ArtistPlaylist_WebAPILoaded_NoCachedContent_PlaylistCreationFailure() = runBlockingTest {
		val playerContext = PlayerContext("artistUri", "Artist", null, "artist")
		val queueImageUri = ImageUri("imageUri")
		val queueCoverArtBitmap: Bitmap = mock()

		val artistSongs = listOf(
				SpotifyMusicMetadata(controller, "mediaId1", 1, "coverArtUri1", "Artist 1", "Album 1", "Title 1"),
				SpotifyMusicMetadata(controller, "mediaId2", 2, "coverArtUri2", "Artist 2", "Album 2", "Title 2")
		)
		whenever(webApi.getArtistTopSongs(controller, playerContext.uri)) doAnswer { artistSongs }
		whenever(webApi.createPlaylist(SpotifyWebApi.ARTIST_SONGS_PLAYLIST_NAME, L.MUSIC_TEMPORARY_PLAYLIST_DESCRIPTION)) doAnswer { null }

		playlistCallback.lastValue.onEvent(playerContext)

		verify(webApi, never()).addSongsToPlaylist(any(), any())

		verify(contentApi).getChildrenOfItem(ListItem(playerContext.uri, playerContext.uri, null, playerContext.title, playerContext.subtitle, false, true), 200, 0)
		contentCallback.lastValue.onResult(ListItems(200, 0, 2, arrayOf(
				ListItem("mediaId1", "mediaId1", null, "Title 1", "Subtitle 1", true, false),
				ListItem("mediaId2", "mediaId2", null, "Title 2", "Subtitle 2", true, false)
		)))

		// it should get the QueueMetadata information when called through the Spotify App Remote
		val recentlyPlayedUri = "com.spotify.recently-played"
		verify(contentApi).getChildrenOfItem(ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true), 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(
				ListItem("queueId", "queueUri", queueImageUri, playerContext.title, playerContext.subtitle, false, true)
		)))

		verify(imagesApi).getImage(queueImageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(queueCoverArtBitmap)

		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(playerContext.title, queue!!.title)
		assertEquals(playerContext.subtitle, queue.subtitle)
		assertEquals(queueCoverArtBitmap, queue.coverArt)
		assertNotNull(queue.songs)

		val songs = queue.songs!!
		assertEquals(2, songs.size)
		assertEquals("mediaId1", songs[0].mediaId)
		assertEquals("Title 1", songs[0].title)
		assertEquals("mediaId2", songs[1].mediaId)
		assertEquals("Title 2", songs[1].title)
	}

	@Test
	fun testQueue_ArtistPlaylist_WebAPILoaded_CachedContent_PlaylistDataInvalid() = runBlockingTest {
		val playerContext = PlayerContext("playlistUri", "Artist", null, "artist")
		val queueImageUriStr = "imageUri"
		val queueImageUri = ImageUri(queueImageUriStr)
		val queueCoverArtBitmap: Bitmap = mock()

		val playlistId = "playlistId"
		val playlistUri: PlaylistUri = mock()
		whenever(playlistUri.id) doAnswer { playlistId }
		whenever(playlistUri.uri) doAnswer { playerContext.uri }

		val artistSongs = listOf(
				SpotifyMusicMetadata(controller, "mediaId1", 1, "coverArtUri1", "Artist 1", "Album 1", "Title 1"),
				SpotifyMusicMetadata(controller, "mediaId2", 2, "coverArtUri2", "Artist 2", "Album 2", "Title 2")
		)
		whenever(webApi.getArtistTopSongs(controller, playerContext.uri)) doAnswer { artistSongs }
		whenever(webApi.replacePlaylistSongs(playlistId, artistSongs)) doAnswer { }

		val artistSongsPlaylistState = TemporaryPlaylistState("invalidHashCode", playerContext.uri, playlistId, playerContext.title, playerContext.uri)
		appSettings[AppSettings.KEYS.SPOTIFY_ARTIST_SONGS_PLAYLIST_STATE] = gson.toJson(artistSongsPlaylistState)

		playlistCallback.lastValue.onEvent(playerContext)

		verify(webApi, never()).addSongsToPlaylist(playlistId, artistSongs)
		verify(webApi, never()).createPlaylist(SpotifyWebApi.ARTIST_SONGS_PLAYLIST_NAME, L.MUSIC_TEMPORARY_PLAYLIST_DESCRIPTION)
		verify(webApi).replacePlaylistSongs(playlistId, artistSongs)

		assertEquals(controller.currentPlayerContext.uri, playerContext.uri)

		val recentlyPlayedUri = "com.spotify.recently-played"
		verify(contentApi).getChildrenOfItem(ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true), 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(
				ListItem("queueId", "queueUri", queueImageUri, playerContext.title, playerContext.subtitle, false, true)
		)))

		verify(imagesApi).getImage(queueImageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(queueCoverArtBitmap)

		val expectedQueueMetadata = QueueMetadata(playerContext.title, playerContext.subtitle, artistSongs, queueCoverArtBitmap, playerContext.uri)
		assertEquals(controller.queueMetadata, expectedQueueMetadata)

		val expectedArtistSongsPlaylistState = TemporaryPlaylistState(artistSongs.hashCode().toString(), playerContext.uri, playlistId, playerContext.title, playerContext.uri)
		assertEquals(appSettings[AppSettings.KEYS.SPOTIFY_ARTIST_SONGS_PLAYLIST_STATE], gson.toJson(expectedArtistSongsPlaylistState))

		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(playerContext.title, queue!!.title)
		assertEquals(playerContext.subtitle, queue.subtitle)
		assertEquals(queueCoverArtBitmap, queue.coverArt)
		assertNotNull(queue.songs)

		val songs = queue.songs!!
		assertEquals(2, songs.size)
		assertEquals("mediaId1", songs[0].mediaId)
		assertEquals("Title 1", songs[0].title)
		assertEquals("mediaId2", songs[1].mediaId)
		assertEquals("Title 2", songs[1].title)
	}

	@Test
	fun testQueue_ArtistPlaylist_WebAPILoaded_CachedContent_PlaylistDataInvalid_CachedPlaylistDifferentThanCurrent() = runBlockingTest {
		val playerContext = PlayerContext("playlistUri", "Artist", null, "artist")
		val queueImageUriStr = "imageUri"
		val queueImageUri = ImageUri(queueImageUriStr)
		val queueCoverArtBitmap: Bitmap = mock()

		val temporaryPlaylistUri = "temporaryPlaylistUri"
		val originalPlaylistUri = "originalPlaylistUri"
		val playlistId = "playlistId"

		val artistSongs = listOf(
				SpotifyMusicMetadata(controller, "mediaId1", 1, "coverArtUri1", "Artist 1", "Album 1", "Title 1"),
				SpotifyMusicMetadata(controller, "mediaId2", 2, "coverArtUri2", "Artist 2", "Album 2", "Title 2")
		)
		whenever(webApi.getArtistTopSongs(controller, playerContext.uri)) doAnswer { artistSongs }
		whenever(webApi.replacePlaylistSongs(playerContext.uri, artistSongs)) doAnswer { }

		val artistSongsPlaylistState = TemporaryPlaylistState("invalidHashCode", temporaryPlaylistUri, playlistId, playerContext.title, originalPlaylistUri)
		appSettings[AppSettings.KEYS.SPOTIFY_ARTIST_SONGS_PLAYLIST_STATE] = gson.toJson(artistSongsPlaylistState)

		playlistCallback.lastValue.onEvent(playerContext)

		val recentlyPlayedUri = "com.spotify.recently-played"
		val recentlyPlayedRequestListItem = ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true)
		val recentlyPlayedListItemResponse = ListItem("queueId", "queueUri", queueImageUri, playerContext.title, playerContext.subtitle, false, true)
		verify(contentApi).getChildrenOfItem(recentlyPlayedRequestListItem, 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(recentlyPlayedListItemResponse)))

		assertEquals(controller.currentPlayerContext.title, playerContext.title)

		verify(webApi, never()).addSongsToPlaylist(playlistId, artistSongs)
		verify(webApi, never()).createPlaylist(SpotifyWebApi.ARTIST_SONGS_PLAYLIST_NAME, L.MUSIC_TEMPORARY_PLAYLIST_DESCRIPTION)
		verify(webApi).replacePlaylistSongs(playlistId, artistSongs)

		assertEquals(controller.currentPlayerContext.uri, temporaryPlaylistUri)

		verify(contentApi, times(2)).getChildrenOfItem(recentlyPlayedRequestListItem, 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(recentlyPlayedListItemResponse)))

		verify(imagesApi).getImage(queueImageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(queueCoverArtBitmap)

		val expectedQueueMetadata = QueueMetadata(playerContext.title, playerContext.subtitle, artistSongs, queueCoverArtBitmap, temporaryPlaylistUri)
		assertEquals(controller.queueMetadata, expectedQueueMetadata)

		val expectedArtistSongsPlaylistState = TemporaryPlaylistState(artistSongs.hashCode().toString(), temporaryPlaylistUri, playlistId, playerContext.title, originalPlaylistUri)
		assertEquals(appSettings[AppSettings.KEYS.SPOTIFY_ARTIST_SONGS_PLAYLIST_STATE], gson.toJson(expectedArtistSongsPlaylistState))

		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(playerContext.title, queue!!.title)
		assertEquals(playerContext.subtitle, queue.subtitle)
		assertEquals(queueCoverArtBitmap, queue.coverArt)
		assertNotNull(queue.songs)

		val songs = queue.songs!!
		assertEquals(2, songs.size)
		assertEquals("mediaId1", songs[0].mediaId)
		assertEquals("Title 1", songs[0].title)
		assertEquals("mediaId2", songs[1].mediaId)
		assertEquals("Title 2", songs[1].title)
	}

	@Test
	fun testQueue_ArtistPlaylist_WebAPILoaded_ArtistTemporaryPlaylistInContext_CachedContent() = runBlockingTest {
		val playerContext = PlayerContext("playlistUri", SpotifyWebApi.ARTIST_SONGS_PLAYLIST_NAME, null, "artist")
		val queueImageUriStr = "imageUri"
		val queueImageUri = ImageUri(queueImageUriStr)
		val queueCoverArtBitmap: Bitmap = mock()

		val originalPlaylistUri = "originalPlaylistUri"
		val playlistId = "playlistId"

		val artistSongs = listOf(
				SpotifyMusicMetadata(controller, "mediaId1", 1, "coverArtUri1", "Artist 1", "Album 1", "Title 1"),
				SpotifyMusicMetadata(controller, "mediaId2", 2, "coverArtUri2", "Artist 2", "Album 2", "Title 2")
		)
		whenever(webApi.getArtistTopSongs(controller, originalPlaylistUri)) doAnswer { artistSongs }

		val artistSongsPlaylistState = TemporaryPlaylistState(artistSongs.hashCode().toString(), playerContext.uri, playlistId, playerContext.title, originalPlaylistUri)
		appSettings[AppSettings.KEYS.SPOTIFY_ARTIST_SONGS_PLAYLIST_STATE] = gson.toJson(artistSongsPlaylistState)

		playlistCallback.lastValue.onEvent(playerContext)

		verify(webApi, never()).addSongsToPlaylist(playlistId, artistSongs)
		verify(webApi, never()).createPlaylist(SpotifyWebApi.ARTIST_SONGS_PLAYLIST_NAME, L.MUSIC_TEMPORARY_PLAYLIST_DESCRIPTION)
		verify(webApi, never()).replacePlaylistSongs(playlistId, artistSongs)

		val recentlyPlayedUri = "com.spotify.recently-played"
		verify(contentApi).getChildrenOfItem(ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true), 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(
				ListItem("queueId", "queueUri", queueImageUri, playerContext.title, playerContext.subtitle, false, true)
		)))

		verify(imagesApi).getImage(queueImageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(queueCoverArtBitmap)

		val expectedQueueMetadata = QueueMetadata(playerContext.title, playerContext.subtitle, artistSongs, queueCoverArtBitmap, playerContext.uri)
		assertEquals(controller.queueMetadata, expectedQueueMetadata)

		val expectedArtistSongsPlaylistState = TemporaryPlaylistState(artistSongs.hashCode().toString(), playerContext.uri, playlistId, playerContext.title, originalPlaylistUri)
		assertEquals(appSettings[AppSettings.KEYS.SPOTIFY_ARTIST_SONGS_PLAYLIST_STATE], gson.toJson(expectedArtistSongsPlaylistState))

		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(playerContext.title, queue!!.title)
		assertEquals(playerContext.subtitle, queue.subtitle)
		assertEquals(queueCoverArtBitmap, queue.coverArt)
		assertNotNull(queue.songs)

		val songs = queue.songs!!
		assertEquals(2, songs.size)
		assertEquals("mediaId1", songs[0].mediaId)
		assertEquals("Title 1", songs[0].title)
		assertEquals("mediaId2", songs[1].mediaId)
		assertEquals("Title 2", songs[1].title)
	}

	@Test
	fun testQueue_ArtistPlaylist_WebAPINotLoaded() = runBlockingTest {
		val playerContext = PlayerContext("playlistUri", "Artist", null, "artist")
		val queueImageUri = ImageUri("imageUri")
		val queueCoverArtBitmap: Bitmap = mock()

		whenever(webApi.getArtistTopSongs(controller, playerContext.uri)) doAnswer { emptyList() }

		playlistCallback.lastValue.onEvent(playerContext)

		verify(webApi, never()).getPlaylistSongs(controller, playerContext.uri)
		verify(contentApi).getChildrenOfItem(ListItem(playerContext.uri, playerContext.uri, null, playerContext.title, playerContext.subtitle, false, true), 200, 0)
		contentCallback.lastValue.onResult(ListItems(200, 0, 2, arrayOf(
				ListItem("mediaId1", "mediaId1", null, "Title 1", "Subtitle 1", true, false),
				ListItem("mediaId2", "mediaId2", null, "Title 2", "Subtitle 2", true, false)
		)))

		// it should get the QueueMetadata information when called through the Spotify App Remote
		val recentlyPlayedUri = "com.spotify.recently-played"
		verify(contentApi).getChildrenOfItem(ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true), 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(
				ListItem("queueId", "queueUri", queueImageUri, playerContext.title, playerContext.subtitle, false, true)
		)))

		verify(imagesApi).getImage(queueImageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(queueCoverArtBitmap)

		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(playerContext.title, queue!!.title)
		assertEquals(playerContext.subtitle, queue.subtitle)
		assertEquals(queueCoverArtBitmap, queue.coverArt)
		assertNotNull(queue.songs)

		val songs = queue.songs!!
		assertEquals(2, songs.size)
		assertEquals("mediaId1", songs[0].mediaId)
		assertEquals("Title 1", songs[0].title)
		assertEquals("mediaId2", songs[1].mediaId)
		assertEquals("Title 2", songs[1].title)
	}

	@Test
	fun testQueue_PodcastPlaylist() = runBlockingTest {
		val playerContext = PlayerContext("playlistUri", "title", "subtitle", "show")
		val queueImageUri = ImageUri("imageUri")
		val queueCoverArtBitmap: Bitmap = mock()

		// load a queue
		playlistCallback.lastValue.onEvent(playerContext)
		verify(webApi).clearPendingQueueMetadataCreate()
		verify(contentApi).getChildrenOfItem(ListItem(playerContext.uri, playerContext.uri, null, playerContext.title, playerContext.subtitle, false, true), 200, 0)
		contentCallback.lastValue.onResult(ListItems(200, 0, 1, arrayOf(
				ListItem("id", "uri", null, "Title", "Subtitle", true, false)
		)))

		// it should get the QueueMetadata information
		val recentlyPlayedUri = "com.spotify.recently-played"
		verify(contentApi).getChildrenOfItem(ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true), 1, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(
				ListItem("queueId", "queueUri", queueImageUri, playerContext.title, playerContext.subtitle, false, true)
		)))

		verify(imagesApi).getImage(queueImageUri, Image.Dimension.THUMBNAIL)
		imagesCallback.lastValue.onResult(queueCoverArtBitmap)

		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(playerContext.title, queue!!.title)
		assertEquals(playerContext.subtitle, queue.subtitle)
		assertEquals(queueCoverArtBitmap, queue.coverArt)
		assertNotNull(queue.songs)

		val songs = queue.songs!!
		assertEquals(1, songs.size)
		assertNotEquals(null, songs[0].queueId)
		assertEquals("Title", songs[0].title)
	}

	@Test
	fun testStateUpdate() {
		val state = PlayerState(
				Track(
					Artist("artist", "uri"),
					listOf(Artist("artist", "uri")),
					Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
				false, 1.0f, 200,
				PlayerOptions.DEFAULT, PlayerRestrictions.DEFAULT
		)
		spotifyCallback.lastValue.onEvent(state)

		val metadata = controller.getMetadata()
		val position = controller.getPlaybackPosition()
		assertEquals("name", metadata!!.title)
		assertEquals("artist", metadata.artist)
		assertEquals("album", metadata.album)
		assertEquals(null, metadata.coverArt)
		assertEquals(200, position.lastPosition)
		assertEquals(false, position.isPaused)
		assertFalse(controller.isSupportedAction(MusicAction.SKIP_TO_PREVIOUS))
		assertFalse(controller.isSupportedAction(MusicAction.SKIP_TO_NEXT))
		assertFalse(controller.isSupportedAction(MusicAction.SEEK_TO))
		assertTrue(controller.isSupportedAction(MusicAction.PLAY))
		assertTrue(controller.isSupportedAction(MusicAction.PAUSE))
		assertFalse(controller.isSupportedAction(MusicAction.SET_SHUFFLE_MODE))

		// resolve the cover art
		imagesCallback.lastValue.onResult(mock())
		assertNotEquals(null, controller.getMetadata()?.coverArt)

		// set the player restrictions for skipping and seeking
		val premiumState = PlayerState(
				Track(
						Artist("artist", "uri"),
						listOf(Artist("artist", "uri")),
						Album("album", "uri"), 300000, "name", "uri", mock(), false, false),
				false, 1.0f, 200,
				PlayerOptions.DEFAULT, PlayerRestrictions(true, true, true, true, true, true)
		)
		spotifyCallback.lastValue.onEvent(premiumState)
		assertTrue(controller.isSupportedAction(MusicAction.SKIP_TO_PREVIOUS))
		assertTrue(controller.isSupportedAction(MusicAction.SKIP_TO_NEXT))
		assertTrue(controller.isSupportedAction(MusicAction.SEEK_TO))
	}

	@Test
	fun testBrowse() {
		runBlocking {
			val deferredResults = async {
				controller.browse(null)
			}
			delay(1000)
			assertFalse(deferredResults.isCompleted)
			verify(contentApi).getRecommendedContentItems("default")
			contentCallback.lastValue.onResult(ListItems(1, 0, 1, arrayOf(
					ListItem("id", "uri", null, "Title", "Subtitle", true, false)
			)))
			val results = deferredResults.await()
			assertTrue(deferredResults.isCompleted)
			assertEquals(3, results.size)
			assertEquals("Title", results[0].title)
			// these entries are hardcoded to appear here, if they are missing
			assertEquals("Your Library", results[1].title)
			assertEquals("Browse", results[2].title)
		}

		runBlocking {
			val deferredResults = async {
				controller.browse(MusicMetadata(mediaId = "library", browseable = true))
			}
			delay(1000)
			assertFalse(deferredResults.isCompleted)
			verify(contentApi).getChildrenOfItem(ListItem("library", "library", null, null, null, false, true), 200, 0)
			contentCallback.lastValue.onResult(ListItems(1, 0, 2, arrayOf(
					ListItem("id", "uri", null, "Favorite", "Subtitle", true, false)
			)))
			// it should check again
			verify(contentApi).getChildrenOfItem(ListItem("library", "library", null, null, null, false, true), 200, 1)
			contentCallback.lastValue.onResult(ListItems(200, 1, 2, arrayOf(
					ListItem("id2", "uri", null, "Favorite2", "Subtitle", true, false)
			)))
			val results = deferredResults.await()
			assertTrue(deferredResults.isCompleted)
			assertEquals("Favorite", results[0].title)
			assertEquals("Favorite2", results[1].title)
		}
	}

	@Test
	fun testBrowse_LikedSongsTemporaryPlaylist() = runBlockingTest {
		val deferredResults = async {
			controller.browse(MusicMetadata(mediaId = "library", browseable = true))
		}
		delay(1000)
		assertFalse(deferredResults.isCompleted)
		val listItem = ListItem("library", "library", null, null, null, false, true)
		verify(contentApi).getChildrenOfItem(listItem, 200, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 3, arrayOf(
				ListItem("id1", "uri1", null, "Favorite", "Subtitle", true, false)
		)))
		verify(contentApi).getChildrenOfItem(listItem, 200, 1)
		contentCallback.lastValue.onResult(ListItems(1, 1, 3, arrayOf(
				ListItem("id2", "uri2", null, SpotifyWebApi.LIKED_SONGS_PLAYLIST_NAME, "Subtitle", false, true)
		)))
		verify(contentApi).getChildrenOfItem(listItem, 200, 2)
		contentCallback.lastValue.onResult(ListItems(200, 2, 3, arrayOf(
				ListItem("id3", "uri3", null, "Favorite2", "Subtitle", true, false)
		)))

		val results = deferredResults.await()
		assertTrue(deferredResults.isCompleted)
		assertEquals(results.size, 2)
		assertEquals("Favorite", results[0].title)
		assertEquals("Favorite2", results[1].title)
	}

	@Test
	fun testBrowse_ArtistTemporaryPlaylist() = runBlockingTest {
		val deferredResults = async {
			controller.browse(MusicMetadata(mediaId = "library", browseable = true))
		}
		delay(1000)
		assertFalse(deferredResults.isCompleted)
		val listItem = ListItem("library", "library", null, null, null, false, true)
		verify(contentApi).getChildrenOfItem(listItem, 200, 0)
		contentCallback.lastValue.onResult(ListItems(1, 0, 3, arrayOf(
				ListItem("id1", "uri1", null, "Favorite", "Subtitle", true, false)
		)))
		verify(contentApi).getChildrenOfItem(listItem, 200, 1)
		contentCallback.lastValue.onResult(ListItems(1, 1, 3, arrayOf(
				ListItem("id2", "uri2", null, SpotifyWebApi.ARTIST_SONGS_PLAYLIST_NAME, "Subtitle", false, true)
		)))
		verify(contentApi).getChildrenOfItem(listItem, 200, 2)
		contentCallback.lastValue.onResult(ListItems(200, 2, 3, arrayOf(
				ListItem("id3", "uri3", null, "Favorite2", "Subtitle", true, false)
		)))

		val results = deferredResults.await()
		assertTrue(deferredResults.isCompleted)
		assertEquals(results.size, 2)
		assertEquals("Favorite", results[0].title)
		assertEquals("Favorite2", results[1].title)
	}

	@Test
	fun testSearch() {
		runBlocking {
			assertTrue(controller.search("any").isEmpty())
		}
	}

	@Test
	fun testDisconnect() {
		controller.subscribe {  }
		assertNotEquals(null, controller.callback)
		controller.disconnect()
		assertEquals(null, controller.callback)
		verify(controller.spotifySubscription).cancel()
		verify(controller.playlistSubscription).cancel()
		verify(webApi).disconnect()
	}

	@Test
	fun testToString() {
		assertEquals("SpotifyAppController", controller.toString())
	}
}