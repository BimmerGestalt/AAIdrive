package me.hufman.androidautoidrive.music

import com.nhaarman.mockito_kotlin.doAnswer
import me.hufman.androidautoidrive.carapp.music.TextScroller
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(System::class, TextScroller::class)
class TextScrollerTest {
	val longText = "This is a long text segment that will scroll"

	@Test
	fun testGetText_ShouldNotScroll() {
		val shortText = "some text"
		PowerMockito.mockStatic(System::class.java)
		PowerMockito.`when`(System.currentTimeMillis())
				.doAnswer { 0L } // initialization

		val textScroller = TextScroller(shortText, 30)

		assertEquals(shortText, textScroller.getText())
		assertEquals(shortText, textScroller.getText())
	}

	@Test
	fun testGetText_TimeElapsedLessThanScrollCooldown() {
		PowerMockito.mockStatic(System::class.java)
		PowerMockito.`when`(System.currentTimeMillis())
				.doAnswer { 0L } // initialization
				.doAnswer { 1000L } // elapsed time
				.doAnswer { 1001L } // elapsed time
		val textScroller = TextScroller(longText, 40)

		assertEquals(longText, textScroller.getText())
		assertEquals(longText, textScroller.getText())
	}

	@Test
	fun testGetText_TimeElapsedAfterScrollCooldown() {
		PowerMockito.mockStatic(System::class.java)
		PowerMockito.`when`(System.currentTimeMillis())
				.doAnswer { 0L } // initialization
				.doAnswer { 10000L } // elapsed time
				.doAnswer { 10001L } // elapsed time
		val textScroller = TextScroller(longText, 40)
		textScroller.getText()

		assertEquals(longText, textScroller.getText())
		assertEquals(longText.substring(3, longText.length), textScroller.getText())
		assertEquals(longText.substring(6, longText.length), textScroller.getText())
	}

	@Test
	fun testGetText_TextScroll_SliceHasNotReachedEndOfOriginalText() {
		PowerMockito.mockStatic(System::class.java)
		PowerMockito.`when`(System.currentTimeMillis())
				.doAnswer { 0L } // initialization
				.doAnswer { 10000L } // elapsed time
		val textScroller = TextScroller(longText, 40)
		textScroller.getText()

		assertEquals(longText, textScroller.getText())
		assertEquals(longText.substring(3, longText.length), textScroller.getText())
		assertEquals(longText.substring(6, longText.length), textScroller.getText())
	}

	@Test
	fun testGetText_TextScroll_SliceReachedEndOfOriginalText() {
		PowerMockito.mockStatic(System::class.java)
		PowerMockito.`when`(System.currentTimeMillis())
				.doAnswer { 0L } // initialization
				.doAnswer { 10000L } // elapsed time
				.doAnswer { 20000L } // new previous timestamp
				.doAnswer { 21000L } // elapsed time
		val textScroller = TextScroller(longText, 40)
		textScroller.getText()
		textScroller.getText()
		textScroller.getText()
		textScroller.getText()
		textScroller.getText()

		assertEquals(longText.substring(12, longText.length), textScroller.getText())
		assertEquals(longText, textScroller.getText())
	}
}