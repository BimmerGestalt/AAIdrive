package me.hufman.androidautoidrive

import com.nhaarman.mockito_kotlin.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.hufman.androidautoidrive.music.CustomAction
import me.hufman.androidautoidrive.music.MusicAction
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.music.controllers.CombinedMusicAppController
import me.hufman.androidautoidrive.music.controllers.MusicAppController
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.lang.UnsupportedOperationException
import java.util.*


@RunWith(MockitoJUnitRunner.Silent::class)
class TestCombinedMusicAppController {

	val leftController = mock<MusicAppController>()
	val rightController = mock<MusicAppController>()
	val leftObservable = MutableObservable<MusicAppController>()
	val rightObservable = MutableObservable<MusicAppController>()
	val controller = CombinedMusicAppController(listOf(leftObservable, rightObservable))

	@Test
	fun testConnectCallback() {
		val callback = mock<(MusicAppController) -> Unit>()
		controller.subscribe(callback)
		assertTrue(controller.isPending())
		assertFalse(controller.isConnected())
		leftObservable.value = null
		verify(callback, never()).invoke(any())
		leftObservable.value = leftController
		verify(callback).invoke(leftController)

		assertTrue(controller.isPending())
		assertTrue(controller.isConnected())

		rightObservable.value = null
		verify(callback, times(1)).invoke(any())
		rightObservable.value = rightController
		verify(callback).invoke(rightController)
		assertFalse(controller.isPending())
		assertTrue(controller.isConnected())
	}

	@Test
	fun testWaitForConnect() {
		runBlocking {
			var connected = false
			launch {
				controller.search("test")
				connected = true
			}
			delay(100)
			assertFalse(connected)
			leftObservable.value = leftController
			rightObservable.value = rightController
			delay(1100)
			assertTrue(connected)
		}
	}

	@Test
	fun testWithController() {
		val callback = mock<(MusicAppController) -> Boolean> {
			onGeneric { invoke(any()) } doReturn true
		}
		val disconnectedResponse = controller.withController(callback)
		assertEquals(null, disconnectedResponse)

		// less preferred controller is connected
		rightObservable.value = rightController
		val connectedResponse = controller.withController(callback)
		assertEquals(true, connectedResponse)
		verify(callback, times(0)).invoke(leftController)
		verify(callback, times(1)).invoke(rightController)

		// preferred controller is connected
		leftObservable.value = leftController
		val leftResponse = controller.withController(callback)
		assertEquals(true, leftResponse)
		verify(callback, times(1)).invoke(leftController)
		verify(callback, times(1)).invoke(rightController)

		// preferred controller raises error
		whenever(callback(leftController)).doThrow(UnsupportedOperationException())
		controller.withController(callback)
		verify(callback, times(2)).invoke(leftController)
		verify(callback, times(2)).invoke(rightController)

		// disconnect preferred
		leftObservable.value = null
		controller.withController(callback)
		verify(callback, times(2)).invoke(leftController)
		verify(callback, times(3)).invoke(rightController)
	}

	@Test
	fun testControl() {
		whenever(leftController.isSupportedAction(any())).doReturn(false)
		whenever(rightController.isSupportedAction(any())).doReturn(true)
		leftObservable.value = leftController
		rightObservable.value = rightController

		// try to find the supported app for the given action
		controller.play()
		verify(leftController, times(1)).isSupportedAction(MusicAction.PLAY)
		verify(rightController, times(1)).isSupportedAction(MusicAction.PLAY)
		verify(leftController, never()).play()
		verify(rightController, times(1)).play()

		controller.pause()
		verify(leftController, times(1)).isSupportedAction(MusicAction.PAUSE)
		verify(rightController, times(1)).isSupportedAction(MusicAction.PAUSE)
		verify(leftController, never()).pause()
		verify(rightController, times(1)).pause()

		controller.skipToPrevious()
		verify(leftController, times(1)).isSupportedAction(MusicAction.SKIP_TO_PREVIOUS)
		verify(rightController, times(1)).isSupportedAction(MusicAction.SKIP_TO_PREVIOUS)
		verify(leftController, never()).skipToPrevious()
		verify(rightController, times(1)).skipToPrevious()

		controller.skipToNext()
		verify(leftController, times(1)).isSupportedAction(MusicAction.SKIP_TO_NEXT)
		verify(rightController, times(1)).isSupportedAction(MusicAction.SKIP_TO_NEXT)
		verify(leftController, never()).skipToNext()
		verify(rightController, times(1)).skipToNext()

		controller.playFromSearch("query")
		verify(leftController, times(1)).isSupportedAction(MusicAction.PLAY_FROM_SEARCH)
		verify(rightController, times(1)).isSupportedAction(MusicAction.PLAY_FROM_SEARCH)
		verify(leftController, never()).playFromSearch(any())
		verify(rightController, times(1)).playFromSearch("query")

		// the first connected app is used for seeking
		controller.seekTo(100)
		verify(leftController).seekTo(100)
		verify(rightController, never()).seekTo(any())

		val customAction = CustomAction("com.musicapp", "action", "name", null, null)
		whenever(rightController.getCustomActions()).thenReturn(listOf(customAction))
		controller.customAction(customAction)
		verify(leftController, times(1)).getCustomActions()
		verify(rightController, times(1)).getCustomActions()
		verify(rightController, times(1)).customAction(customAction)

		whenever(leftController.getQueue()).thenReturn(LinkedList())
		whenever(rightController.getQueue()).thenReturn(listOf(MusicMetadata()))
		controller.playQueue(MusicMetadata(queueId = 1L))
		verify(leftController).getQueue()
		verify(rightController).getQueue()
		verify(leftController, never()).playQueue(any())
		verify(rightController).playQueue(any())
	}

	@Test
	fun testDisconnectedState() {
		assertFalse(controller.isConnected())

		val queue = controller.getQueue()
		verify(leftController, never()).getQueue()
		verify(rightController, never()).getQueue()
		assertTrue(queue.isEmpty())

		val metadata = controller.getMetadata()
		verify(leftController, never()).getMetadata()
		verify(rightController, never()).getMetadata()
		assertEquals(null, metadata)

		val playbackPosition = controller.getPlaybackPosition()
		verify(leftController, never()).getPlaybackPosition()
		verify(rightController, never()).getPlaybackPosition()
		assertEquals(0, playbackPosition.lastPosition)

		val customActions = controller.getCustomActions()
		assertTrue(customActions.isEmpty())
	}

	@Test
	fun testState() {
		leftObservable.value = leftController
		rightObservable.value = rightController

		val metadata = controller.getMetadata()
		verify(leftController, times(1)).getMetadata()
		verify(rightController, never()).getMetadata()

		val playbackPosition = controller.getPlaybackPosition()
		verify(leftController, times(1)).getPlaybackPosition()
		verify(rightController, never()).getPlaybackPosition()

		whenever(leftController.isSupportedAction(any())).thenReturn(false)
		whenever(rightController.isSupportedAction(any())).thenReturn(true)
		val supported = controller.isSupportedAction(MusicAction.PLAY)
		assertTrue(supported)
		verify(leftController, times(1)).isSupportedAction(MusicAction.PLAY)
		verify(rightController, times(1)).isSupportedAction(MusicAction.PLAY)
	}

	@Test
	fun testQueue() {
		leftObservable.value = leftController
		rightObservable.value = rightController
		whenever(leftController.getQueue()).doAnswer { LinkedList() }
		whenever(rightController.getQueue()).doAnswer { listOf(mock()) }

		val queue = controller.getQueue()
		verify(leftController, times(1)).getQueue()
		verify(rightController, times(1)).getQueue()

		assertEquals(1, queue.size)
		controller.playQueue(MusicMetadata(queueId = 0))
		verify(leftController, times(2)).getQueue()
		verify(rightController, times(2)).getQueue()
		verify(leftController, times(0)).playQueue(any())
		verify(rightController, times(1)).playQueue(any())
	}

	@Test
	fun testCustomActions() {
		// combine all of the available custom actions
		val leftActions = listOf(CustomAction("test", "action1", "name1", null, null), CustomAction("test", "action2", "shared action", null, null))
		val rightActions = listOf(CustomAction("test", "action3", "name3", null, null), CustomAction("test", "action2", "shared action", mock(), null))
		leftObservable.value = leftController
		rightObservable.value = rightController
		whenever(leftController.getCustomActions()).doAnswer { leftActions }
		whenever(rightController.getCustomActions()).doAnswer { rightActions }

		// get a combined list of custom actions
		val actions = controller.getCustomActions()
		assertEquals(3, actions.size)
		assertSame(leftActions[0], actions[0])
		assertNotSame(leftActions[1], actions[1])
		assertSame(rightActions[1], actions[1])
		assertSame(rightActions[0], actions[2])

		// test that the correct controller gets the action
		controller.customAction(actions[0])
		verify(leftController).customAction(leftActions[0])
		controller.customAction(actions[1])
		verify(leftController, never()).customAction(actions[1])
		verify(rightController).customAction(rightActions[1])
		controller.customAction(actions[2])
		verify(rightController).customAction(rightActions[0])

		// test for looser equality
		val rebuiltAction = CustomAction("test", "action2", "shared action", mock(), null)
		controller.customAction(rebuiltAction)
		verify(leftController).customAction(rebuiltAction)
	}

	@Test
	fun testBrowse() {
		runBlocking {
			leftObservable.value = leftController
			rightObservable.value = rightController

			val musicMetadata = MusicMetadata("mediaId")
			whenever(leftController.browse(anyOrNull())).thenReturn(LinkedList())
			whenever(rightController.browse(anyOrNull())).thenReturn(listOf(musicMetadata))
			val results = controller.browse(MusicMetadata(mediaId="/"))
			assertEquals(listOf(musicMetadata), results)
			verify(leftController, times(1)).browse(anyOrNull())
			verify(rightController, times(1)).browse(anyOrNull())

			// successive browses go to the same controller
			controller.browse(MusicMetadata(mediaId="/"))
			verify(leftController, times(1)).browse(anyOrNull())
			verify(rightController, times(2)).browse(anyOrNull())

			// browsing null will do discovery again
			controller.browse(null)
			verify(leftController, times(2)).browse(anyOrNull())
			verify(rightController, times(3)).browse(anyOrNull())

			// play() should use this same controller
			controller.playSong(musicMetadata)
			verify(leftController, times(0)).playSong(any())
			verify(rightController, times(1)).playSong(any())
		}
	}

	@Test
	fun testSearch() {
		runBlocking {
			leftObservable.value = leftController
			rightObservable.value = rightController

			val musicMetadata = MusicMetadata("mediaId")
			whenever(leftController.search(any())).thenReturn(null)
			whenever(rightController.search(any())).thenReturn(listOf(musicMetadata))
			val results = controller.search("query")
			assertEquals(listOf(musicMetadata), results)
			verify(leftController, times(1)).search(any())
			verify(rightController, times(1)).search(any())

			// play() should use this same controller
			controller.playSong(musicMetadata)
			verify(leftController, times(0)).playSong(any())
			verify(rightController, times(1)).playSong(any())
		}
	}
}