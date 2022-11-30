package me.hufman.androidautoidrive

import android.content.SharedPreferences
import android.os.Handler
import com.nhaarman.mockito_kotlin.*
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundInterruptionDetectionTest {

	data class DetectorState(var lastTimeAlive: Long = 0, var detectedSuspended: Int = 0, var detectedKilled: Int = 0)
	val detectorState = DetectorState()
	var time: Long = 200000

	val runnable = argumentCaptor<Runnable>()
	val handler = mock<Handler> {
		on {postDelayed(runnable.capture(), any())} doReturn true
	}

	val editor = mock<SharedPreferences.Editor> {
		on {putLong(eq("lastTimeAlive"), any())} doAnswer {detectorState.lastTimeAlive = it.arguments[1] as Long; it.mock as SharedPreferences.Editor}
		on {putInt(eq("detectedSuspended"), any())} doAnswer {detectorState.detectedSuspended = it.arguments[1] as Int; it.mock as SharedPreferences.Editor}
		on {putInt(eq("detectedKilled"), any())} doAnswer {detectorState.detectedKilled = it.arguments[1] as Int; it.mock as SharedPreferences.Editor}
	}
	val preferences = mock<SharedPreferences> {
		on {edit()} doReturn editor
		on {getLong(eq("lastTimeAlive"), any())} doAnswer { detectorState.lastTimeAlive }
		on {getInt(eq("detectedSuspended"), any())} doAnswer { detectorState.detectedSuspended }
		on {getInt(eq("detectedKilled"), any())} doAnswer { detectorState.detectedKilled }
	}

	fun buildDetector(): BackgroundInterruptionDetection {
		return BackgroundInterruptionDetection(preferences, handler, BackgroundInterruptionDetection.DEFAULT_TTL) { time }
	}

	@Test
	fun testStart() {
		val detector = buildDetector()
		assertEquals(0L, detector.lastTimeAlive)
		assertEquals(0, detector.detectedKilled)
		assertEquals(0, detector.detectedSuspended)

		detector.start()
		verify(handler).postDelayed(any(), eq(4000))
		assertEquals(time, detector.lastTimeAlive)
		assertEquals(0, detector.detectedKilled)
		assertEquals(0, detector.detectedSuspended)
	}

	@Test
	fun testNormalPause() {
		val detector = buildDetector()
		detector.start()
		time += 8000
		runnable.lastValue.run()
		assertEquals(0, detector.detectedKilled)
		assertEquals(0, detector.detectedSuspended)
		assertEquals(time, detectorState.lastTimeAlive)
	}

	@Test
	fun testLongPause() {
		val detector = buildDetector()
		detector.start()
		time += 16000
		runnable.lastValue.run()
		assertEquals(0, detector.detectedKilled)
		assertEquals(1, detector.detectedSuspended)
		assertEquals(time, detectorState.lastTimeAlive)
	}

	@Test
	fun testHardKill() {
		val detector = buildDetector()
		detector.start()
		// don't run the runnable

		// the shared preferences should be updated
		assertEquals(time, detectorState.lastTimeAlive)

		// new app invocation
		val detector2 = buildDetector()
		detector2.detectKilledPreviously()
		assertEquals(1, detector2.detectedKilled)
		assertEquals(0, detector2.detectedSuspended)
	}

	@Test
	fun testPersistCounters() {
		run {
			val detector = buildDetector()
			detector.start()
			// don't run the runnable
		}

		// new app invocation
		run {
			val detector = buildDetector()
			detector.detectKilledPreviously()
			detector.start()
			assertEquals(1, detector.detectedKilled)
			assertEquals(0, detector.detectedSuspended)

			// lagged
			time += 20000
			runnable.lastValue.run()
			assertEquals(1, detector.detectedKilled)
			assertEquals(1, detector.detectedSuspended)
			detector.safelyStop()
		}

		// reader
		run {
			val detector = buildDetector()
			detector.start()
			// still remember the previous counters
			assertEquals(1, detector.detectedKilled)
			assertEquals(1, detector.detectedSuspended)
			// loop for 90 seconds
			(0..10).forEach {
				time += 9000
				runnable.lastValue.run()
			}
			// shut down, shouldn't clear counters
			detector.safelyStop()
			assertEquals(1, detectorState.detectedKilled)
			assertEquals(1, detectorState.detectedSuspended)
		}

		// reader
		run {
			val detector = buildDetector()
			detector.start()
			// still remember the previous counters
			assertEquals(1, detector.detectedKilled)
			assertEquals(1, detector.detectedSuspended)
			// loop for 10 minutes
			(0..70).forEach {
				time += 9000
				runnable.lastValue.run()
			}
			// shut down, should clear counters
			detector.safelyStop()
			assertEquals(0, detectorState.detectedKilled)
			assertEquals(0, detectorState.detectedSuspended)
		}
	}
}