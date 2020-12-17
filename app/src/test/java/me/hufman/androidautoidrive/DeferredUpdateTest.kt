package me.hufman.androidautoidrive

import android.os.Handler
import com.nhaarman.mockito_kotlin.*
import org.junit.Test

class DeferredUpdateTest {
	val handler = mock<Handler>()

	@Test
	fun testDeferredUpdate() {
		var currentTime: Long = 4000
		val timeProvider = {currentTime}
		val deferredUpdate = DeferredUpdate(handler, timeProvider)

		deferredUpdate.defer(2000)
		verifyNoMoreInteractions(handler)   // should not have scheduled anything

		deferredUpdate.trigger { }
		verify(handler).postDelayed(any(), eq(2000))
		reset(handler)

		// a bit later, try to trigger again
		currentTime = 5000
		deferredUpdate.trigger { }
		verify(handler).postDelayed(any(), eq(1000))
		reset(handler)

		// right before the end of deferred, check that the delay is still used
		currentTime = 5990
		deferredUpdate.trigger { }
		verify(handler).postDelayed(any(), eq(100)) // trigger has a default delay
		reset(handler)

		// after the deferred, try to trigger
		currentTime = 7000
		deferredUpdate.trigger { }
		verify(handler).postDelayed(any(), eq(100)) // trigger has a default delay
		reset(handler)
	}
}