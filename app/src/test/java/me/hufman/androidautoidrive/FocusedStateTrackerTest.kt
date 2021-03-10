package me.hufman.androidautoidrive

import me.hufman.androidautoidrive.carapp.FocusedStateTracker
import org.junit.Assert.*
import org.junit.Test

class FocusedStateTrackerTest {
	@Test
	fun testFocused() {
		val focusTracker = FocusedStateTracker()
		assertEquals(null, focusTracker.getFocused())

		focusTracker.onFocus(30, false)
		assertEquals(null, focusTracker.getFocused())
		focusTracker.onFocus(30, true)
		assertEquals(30, focusTracker.getFocused())

		focusTracker.onFocus(10)
		assertEquals(10, focusTracker.getFocused())

		focusTracker.onBlur(30)
		assertEquals(10, focusTracker.getFocused())
		focusTracker.onBlur(10)
		assertEquals(null, focusTracker.getFocused())
	}
}