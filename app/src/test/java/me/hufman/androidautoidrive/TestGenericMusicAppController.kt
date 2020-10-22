package me.hufman.androidautoidrive

import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.nhaarman.mockito_kotlin.*
import kotlinx.coroutines.runBlocking
import me.hufman.androidautoidrive.music.*
import me.hufman.androidautoidrive.music.controllers.GenericMusicAppController
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test


class TestGenericMusicAppController {
	val mediaTransportControls = mock<MediaControllerCompat.TransportControls>()
	val mediaController = mock<MediaControllerCompat> {
		on { transportControls } doReturn mediaTransportControls
		on { packageName } doReturn "com.musicapp"
	}
	val musicBrowser = mock<MusicBrowser> {
		on { connected } doReturn true
	}
	lateinit var controller: GenericMusicAppController

	fun createPlaybackState(stateValue: Int, positionValue: Long, actionsValue: Long): PlaybackStateCompat {
		return mock {
			on { state } doReturn stateValue
			on { position } doReturn positionValue
			on { actions } doReturn actionsValue
		}
	}

	@Before
	fun setup() {
		controller = GenericMusicAppController(mock(), mediaController, musicBrowser)
	}

	@Test
	fun testControl() {
		controller.play()
		verify(mediaTransportControls).play()

		controller.pause()
		verify(mediaTransportControls).pause()

		controller.skipToPrevious()
		verify(mediaTransportControls).skipToPrevious()

		controller.skipToNext()
		verify(mediaTransportControls).skipToNext()

		controller.seekTo(100)
		verify(mediaTransportControls).seekTo(100)

		val song = MusicMetadata(mediaId = "test", queueId = 2)
		controller.playSong(song)
		verify(mediaTransportControls).playFromMediaId(song.mediaId, null)
		controller.playQueue(song)
		verify(mediaTransportControls).skipToQueueItem(song.queueId as Long)

		controller.playFromSearch("query")
		verify(mediaTransportControls).playFromSearch("query", null)
	}

	@Test
	fun testSupportedAction() {
		whenever(mediaController.playbackState) doAnswer {
			createPlaybackState(0, 0, MusicAction.PLAY.flag)
		}
		assertTrue(controller.isSupportedAction(MusicAction.PLAY))
	}

	@Test
	fun testCustomActions() {
		val otherAction = CustomAction("com.wrongapp", "test", "Name", null, null)
		controller.customAction(otherAction)
		verify(mediaTransportControls, never()).sendCustomAction(any<String>(), anyOrNull())

		val myAction = CustomAction("com.musicapp", "test", "Name", null, null)
		controller.customAction(myAction)
		verify(mediaTransportControls).sendCustomAction(myAction.action, null)

		// empty playbackstate, empty actions
		val emptyActions = controller.getCustomActions()
		assertEquals(0, emptyActions.size)

		// a mock action, except the context for CustomAction.fromMediaCustomAction is hard to mock
		/*
		val playbackState = createPlaybackState(PlaybackStateCompat.STATE_PLAYING, 1000)
		whenever(playbackState.customActions) doAnswer {
			listOf(mock {
				on { action } doReturn "test"
				on { name } doReturn "Name"
			})
		}
		whenever(mediaController.playbackState) doReturn playbackState
		val expectedAction = CustomAction("com.musicapp", "test", "Name", null, null)
		val parsedAction = controller.getCustomActions()
		assertEquals(1, parsedAction.size)
		assertEquals(expectedAction, parsedAction[0])
		*/
	}

	@Test
	fun testIsShuffling() {
		whenever(mediaController.shuffleMode) doAnswer {
			PlaybackStateCompat.SHUFFLE_MODE_ALL
		}
		assertTrue(controller.isShuffling())

		whenever(mediaController.shuffleMode) doAnswer {
			PlaybackStateCompat.SHUFFLE_MODE_GROUP
		}
		assertTrue(controller.isShuffling())

		whenever(mediaController.shuffleMode) doAnswer {
			PlaybackStateCompat.SHUFFLE_MODE_NONE
		}
		assertFalse(controller.isShuffling())

		whenever(mediaController.shuffleMode) doAnswer {
			PlaybackStateCompat.SHUFFLE_MODE_INVALID
		}
		assertFalse(controller.isShuffling())
	}

	@Test
	fun testToggleShuffle() {
		whenever(mediaController.shuffleMode) doAnswer {
			PlaybackStateCompat.SHUFFLE_MODE_NONE
		}
		controller.toggleShuffle()
		verify(mediaTransportControls).setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL)

		whenever(mediaController.shuffleMode) doAnswer {
			PlaybackStateCompat.SHUFFLE_MODE_ALL
		}
		controller.toggleShuffle()
		verify(mediaTransportControls).setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE)
	}

	@Test
	fun testGetRepeatMode() {
		whenever(mediaController.repeatMode) doAnswer {
			PlaybackStateCompat.REPEAT_MODE_NONE
		}
		assertEquals(RepeatMode.OFF, controller.getRepeatMode())

		whenever(mediaController.repeatMode) doAnswer {
			PlaybackStateCompat.REPEAT_MODE_ALL
		}
		assertEquals(RepeatMode.ALL, controller.getRepeatMode())

		whenever(mediaController.repeatMode) doAnswer {
			PlaybackStateCompat.REPEAT_MODE_ONE
		}
		assertEquals(RepeatMode.ONE, controller.getRepeatMode())
	}

	@Test
	fun testRepeatToggle() {
		whenever(mediaController.repeatMode) doAnswer {
			PlaybackStateCompat.REPEAT_MODE_NONE
		}
		controller.toggleRepeat()
		verify(mediaTransportControls).setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL)

		whenever(mediaController.repeatMode) doAnswer {
			PlaybackStateCompat.REPEAT_MODE_ALL
		}
		controller.toggleRepeat()
		verify(mediaTransportControls).setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)

		whenever(mediaController.repeatMode) doAnswer {
			PlaybackStateCompat.REPEAT_MODE_ONE
		}
		controller.toggleRepeat()
		verify(mediaTransportControls).setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)
	}

	@Test
	fun testQueue() {
		val mediaDescription = mock<MediaDescriptionCompat> {
			on { iconBitmap } doAnswer { mock() }
			on { title } doReturn "test title"
		}
		val queueTitle = "queue title"
		whenever(mediaController.queueTitle) doAnswer { queueTitle }
		whenever(mediaController.queue) doAnswer {
			listOf(mock {
				on { queueId } doReturn 2L
				on { description } doReturn mediaDescription
			})
		}
		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(queueTitle, queue!!.title)
		assertNull(queue.subtitle)

		val songs = queue.songs
		assertNotNull(queue.songs)
		assertEquals(1, songs!!.size)
		assertEquals(2L, songs[0].queueId)
		assertEquals("test title", songs[0].title)
	}

	@Test
	fun testMetadata() {
		whenever(mediaController.metadata) doAnswer {
			mock {
				on { getString(any()) } doReturn null as String?
				on { getString("android.media.metadata.TITLE") } doReturn "test title"
				on { bundle } doAnswer { mock() }
			}
		}
		val metadata = controller.getMetadata()
		assertEquals("test title", metadata?.title)
	}

	@Test
	fun testPlaybackPosition() {
		whenever(mediaController.playbackState) doAnswer { createPlaybackState(PlaybackStateCompat.STATE_PAUSED, 1000, 0) }
		val playbackPosition = controller.getPlaybackPosition()
		assertTrue(playbackPosition.playbackPaused)
		assertEquals(1000, playbackPosition.lastPosition)

		whenever(mediaController.playbackState) doAnswer { createPlaybackState(PlaybackStateCompat.STATE_BUFFERING, 1000, 0) }
		assertTrue(controller.getPlaybackPosition().playbackPaused)

		whenever(mediaController.playbackState) doAnswer { createPlaybackState(PlaybackStateCompat.STATE_CONNECTING, 1000, 0) }
		assertTrue(controller.getPlaybackPosition().playbackPaused)

		whenever(mediaController.playbackState) doAnswer { createPlaybackState(PlaybackStateCompat.STATE_STOPPED, 1000, 0) }
		assertTrue(controller.getPlaybackPosition().playbackPaused)

		whenever(mediaController.playbackState) doAnswer { createPlaybackState(PlaybackStateCompat.STATE_NONE, 1000, 0) }
		assertTrue(controller.getPlaybackPosition().playbackPaused)

		whenever(mediaController.playbackState) doAnswer { createPlaybackState(PlaybackStateCompat.STATE_PLAYING, 1000, 0) }
		assertFalse(controller.getPlaybackPosition().playbackPaused)

		whenever(mediaController.playbackState) doAnswer { createPlaybackState(PlaybackStateCompat.STATE_PLAYING or PlaybackStateCompat.STATE_BUFFERING, 1000, 0) }
		assertFalse(controller.getPlaybackPosition().playbackPaused)

		whenever(mediaController.playbackState) doReturn null as PlaybackStateCompat?
		val defaultPlaybackPosition = controller.getPlaybackPosition()
		assertTrue(defaultPlaybackPosition.playbackPaused)
		assertEquals(0, defaultPlaybackPosition.lastPosition)
		assertEquals(0, defaultPlaybackPosition.maximumPosition)
	}

	@Test
	fun testBrowse() {
		// null results
		runBlocking {
			val results = controller.browse(null)
			assertEquals(0, results.size)
			verify(musicBrowser).browse(null)
		}

		runBlocking {
			val root = MusicMetadata(mediaId = "/")
			val descriptionValue = mock<MediaDescriptionCompat> {
				on { title } doReturn "title"
				on { extras } doAnswer { mock {
					on { getString("android.media.metadata.ARTIST") } doReturn "Artist"
				}}
			}
			whenever(musicBrowser.browse("/")) doAnswer {
				listOf(mock {
					on { mediaId } doReturn "mediaID"
					on { description } doReturn descriptionValue
				})
			}
			val results = controller.browse(root)
			assertEquals(1, results.size)
			assertEquals("mediaID", results[0].mediaId)
			assertEquals("title", results[0].title)
			assertEquals("Artist", results[0].artist)
			verify(musicBrowser).browse("/")
		}
	}

	@Test
	fun testSearch() {
		// null results
		runBlocking {
			val results = controller.search("")
			assertEquals(null, results)
			verify(musicBrowser).search("")
		}

		runBlocking {
			val descriptionValue = mock<MediaDescriptionCompat> {
				on { title } doReturn "title"
				on { extras } doAnswer { mock {
					on { getString("android.media.metadata.ARTIST") } doReturn "Artist"
				}}
			}
			whenever(musicBrowser.search("query")) doAnswer {
				listOf(mock {
					on { mediaId } doReturn "mediaID"
					on { description } doReturn descriptionValue
				})
			}
			val results = controller.search("query")
			assertEquals(1, results!!.size)
			assertEquals("mediaID", results[0].mediaId)
			assertEquals("title", results[0].title)
			assertEquals("Artist", results[0].artist)
			verify(musicBrowser).search("query")
		}
	}

	@Test
	fun testDisconnect() {
		controller.disconnect()
		// tries to load up controllerCallback, which crashes
		// and so it never gets to unregisterCallback
//		verify(mediaController).unregisterCallback(any())
		verify(musicBrowser).disconnect()
	}

	@Test
	fun testToString() {
		assertEquals("GenericMusicAppController(com.musicapp,true)", controller.toString())
	}
}