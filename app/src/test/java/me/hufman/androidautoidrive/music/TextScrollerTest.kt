package me.hufman.androidautoidrive.music

import me.hufman.androidautoidrive.carapp.music.TextScroller
import org.junit.Assert.*
import org.junit.Test

class TextScrollerTest {
	val longText = "This is a long text segment that will scroll"

	@Test
	fun testGetText_ShouldNotScroll() {
		val shortText = "some text"

		val textScroller = TextScroller(shortText, 30) { 0 }

		assertEquals(shortText, textScroller.getText())
		assertEquals(shortText, textScroller.getText())
	}

	@Test
	fun testGetText_TimeElapsedLessThanScrollCooldown() {
		val times = listOf(0L, 1000L, 1001L).iterator()
		val textScroller = TextScroller(longText, 40) { times.next() }

		assertEquals(longText, textScroller.getText())
		assertEquals(longText, textScroller.getText())
	}

	@Test
	fun testGetText_TimeElapsedAfterScrollCooldown() {
		val times = listOf(0L, 10000L, 10001L).iterator()
		val textScroller = TextScroller(longText, 40) { times.next() }
		textScroller.getText()

		assertEquals(longText, textScroller.getText())
		assertEquals(longText.substring(3, longText.length), textScroller.getText())
		assertEquals(longText.substring(6, longText.length), textScroller.getText())
	}

	@Test
	fun testGetText_TextScroll_SliceHasNotReachedEndOfOriginalText() {
		val times = listOf(0L, 10000L).iterator()
		val textScroller = TextScroller(longText, 40) { times.next() }
		textScroller.getText()

		assertEquals(longText, textScroller.getText())
		assertEquals(longText.substring(3, longText.length), textScroller.getText())
		assertEquals(longText.substring(6, longText.length), textScroller.getText())
	}

	@Test
	fun testGetText_TextScroll_SliceReachedEndOfOriginalText() {
		val times = listOf(0L,  // initialization
			10000L, // elapsed time
			20000L, // new previous timestamp
			21000L  // elapsed time
		).iterator()
		val textScroller = TextScroller(longText, 40) { times.next() }
		textScroller.getText()
		textScroller.getText()
		textScroller.getText()
		textScroller.getText()
		textScroller.getText()

		assertEquals(longText.substring(12, longText.length), textScroller.getText())
		assertEquals(longText, textScroller.getText())
	}
}