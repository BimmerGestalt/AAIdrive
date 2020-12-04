package me.hufman.androidautoidrive

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test


class InstrumentedTestNotificationEmoji {
	@Before
	fun setup() {
		UnicodeCleaner.init(InstrumentationRegistry.getInstrumentation().targetContext)
	}

	/** Verifies that foreign letters don't get emoji parsed */
	@Test
	fun testEmojiForeign() {
		run {
			// danish
			val correct = "\u00C6\u00D8\u00C5"
			val parsed = UnicodeCleaner.clean(correct)
			assertEquals(correct, parsed)
		}
		run {
			// kanji
			val correct = "\u732B"
			val parsed = UnicodeCleaner.clean(correct)
			assertEquals(correct, parsed)
		}
	}

	/** Verifies that emoji get parsed */
	@Test
	fun testEmojiParse() {
		run {
			val correct = "I :cat2:!"
			val source = "I \ud83d\udc08!"
			assertEquals(correct, UnicodeCleaner.clean(source))
		}
		run {
			val correct = "I :heart_eyes_cat:!"
			val source = "I \ud83d\ude3b!"
			assertEquals(correct, UnicodeCleaner.clean(source))
		}
	}
}