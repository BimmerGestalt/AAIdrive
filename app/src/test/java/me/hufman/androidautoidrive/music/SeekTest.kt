package me.hufman.androidautoidrive.music

import android.os.Handler
import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.PlaybackPosition
import me.hufman.androidautoidrive.music.SeekingController
import org.junit.Test

class SeekTest {
	@Test
	fun testHoldSeekForward() {
		val handler = mock<Handler>()
		val startPosition: Long = 5000
		val position = mock<PlaybackPosition> {
			on { getPosition() } doReturn startPosition
		}
		val musicController = mock<MusicController> {
			on { getPlaybackPosition() } doReturn position
		}
		var timeMs: Long = 1000
		val seekingRunnable = argumentCaptor<Runnable>()
		val controller = SeekingController(mock(), handler, musicController)
		controller.timeProvider = { timeMs }

		// start a thing
		controller.startFastForward()
		verify(musicController, times(1)).seekTo(startPosition + 4000)
		verify(handler, times(1)).postDelayed(seekingRunnable.capture(), eq(300))

		// the hold timer fires again
		timeMs += 2500   // 2500 later than start
		seekingRunnable.lastValue.run()
		verify(musicController, times(1)).seekTo(startPosition + 7000)
		verify(handler, times(2)).postDelayed(seekingRunnable.capture(), eq(300))

		// the hold timer fires again
		timeMs += 2400   // 4900 later than start
		seekingRunnable.lastValue.run()
		verify(musicController, times(2)).seekTo(startPosition + 7000)
		verify(handler, times(3)).postDelayed(seekingRunnable.capture(), eq(300))

		// release the button
		controller.stopSeeking()
		verify(musicController).play()
		// should not seek or schedule again
		seekingRunnable.lastValue.run()
		verify(musicController, times(2)).seekTo(startPosition + 7000)
		verify(handler, times(3)).postDelayed(seekingRunnable.capture(), eq(300))
	}

	@Test
	fun testHoldSeekBack() {
		val handler = mock<Handler>()
		val startPosition: Long = 5000
		val position = mock<PlaybackPosition> {
			on { getPosition() } doReturn startPosition
		}
		val musicController = mock<MusicController> {
			on { getPlaybackPosition() } doReturn position
		}
		var timeMs: Long = 1000
		val seekingRunnable = argumentCaptor<Runnable>()
		val controller = SeekingController(mock(), handler, musicController)
		controller.timeProvider = { timeMs }

		controller.startRewind()
		verify(musicController).seekTo(startPosition - 4000)
		verify(handler, times(1)).postDelayed(seekingRunnable.capture(), eq(300))

		timeMs += 2500  // 2500 later than start
		seekingRunnable.lastValue.run()
		verify(musicController).skipToPrevious()
	}

	@Test
	fun testSeekAction() {
		val handler = mock<Handler>()
		val startPosition: Long = 25000
		val position = mock<PlaybackPosition> {
			on { getPosition() } doReturn startPosition
		}
		val musicController = mock<MusicController> {
			on { getPlaybackPosition() } doReturn position
		}
		val controller = SeekingController(mock(), handler, musicController)
		controller.seekAction(controller.seekingActions[1]) // back_10
		verify(musicController).seekTo(startPosition - 10000)
		controller.seekAction(controller.seekingActions[3]) // forward_60
		verify(musicController).seekTo(startPosition + 60000)
		controller.seekAction(controller.seekingActions[0]) // back_60
		verify(musicController).skipToPrevious()
	}
}