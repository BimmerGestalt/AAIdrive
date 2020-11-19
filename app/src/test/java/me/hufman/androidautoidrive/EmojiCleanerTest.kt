package me.hufman.androidautoidrive

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test


class EmojiCleanerTest {
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

	/** Verifies that emoji can get encoded */
	@Test
	fun testEmojiEncoding() {
		run {
			val source = "I :cat2:!"
			val correct = "I \ud83d\udc08!"
			assertEquals(correct, UnicodeCleaner.encode(source))
		}
	}

	/** Test out the searching */
	@Test
	fun testEmojiSearching() {
		val corpus = listOf(
				UnicodeCleaner._buildPlaceholderEmoji("\uD83D\uDC08", listOf("cat2"), "cat2"),
				UnicodeCleaner._buildPlaceholderEmoji("\uD83D\uDE3B", listOf("heart_eyes_cat"), "heart_eyes_cat"),
				UnicodeCleaner._buildPlaceholderEmoji("\uD83D\uDC4D", listOf("+1", "thumbsup", "thumbs-up"), "Thumbs Up Sign")
		)

		assertEquals(emptyList<String>(), UnicodeCleaner.searchEmoji(corpus, ""))
		assertEquals(listOf(corpus[0], corpus[1]), UnicodeCleaner.searchEmoji(corpus, "CA"))
		assertEquals(listOf(corpus[1]), UnicodeCleaner.searchEmoji(corpus, "H"))
		assertEquals(listOf(corpus[2]), UnicodeCleaner.searchEmoji(corpus, "+"))
		assertEquals(listOf(corpus[2]), UnicodeCleaner.searchEmoji(corpus, "t"))
		assertEquals(listOf(corpus[2]), UnicodeCleaner.searchEmoji(corpus, "u"))
	}
}