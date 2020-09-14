package me.hufman.androidautoidrive

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test


class TestNotificationEmoji {
	@Before
	fun setup() {
		UnicodeCleaner._addPlaceholderEmoji("\uD83D\uDC08", listOf("cat2"), "cat")
		UnicodeCleaner._addPlaceholderEmoji("\uD83D\uDE3B", listOf("heart_eyes_cat"), "heart_eyes_cat")
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