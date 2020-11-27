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

	/** Test BiDi Isolate Cleaning */
	@Test
	fun testBidiIsolate() {
		run {
			// the car doesn't seem to support Isolates
			val orig = "\u2068+123456789\u2069: Good evening mister."
			val correct = "+123456789: Good evening mister."
			assertEquals(correct, UnicodeCleaner.clean(orig))

			val ending = "\u2068+123456789\u2069: Good evening mister\u2068.\u2069"
			val correctEnd = "+123456789: Good evening mister."
			assertEquals(correctEnd, UnicodeCleaner.clean(ending))
		}

		run {
			// add any explicit embeds to retain the original order
			val orig = "\u2068ltr\u2069: Good evening mister."
			val correct = "\u202Altr\u202C: Good evening mister."
			assertEquals(correct, UnicodeCleaner.clean(orig))

			val origR = "\u2068ء\u2069: Good evening mister."
			val correctR = "\u202Bء\u202C: Good evening mister."
			assertEquals(correctR, UnicodeCleaner.clean(origR))
		}

		run {
			// convert directional isolates to embeds
			val orig = "\u2066ltr\u2069: Good evening mister."
			val correct = "\u202Altr\u202C: Good evening mister."
			assertEquals(correct, UnicodeCleaner.clean(orig))

			val origR = "\u2067rtl\u2069: Good evening mister."
			val correctR = "\u202Brtl\u202C: Good evening mister."
			assertEquals(correctR, UnicodeCleaner.clean(origR))
		}

		run {
			// retain any Explicit Overrides
			val origOrder = "\u2068\u202D+123456789\u202C\u2069: Good evening mister."
			val correctOrder = "\u202D+123456789\u202C: Good evening mister."
			assertEquals(correctOrder, UnicodeCleaner.clean(origOrder))
		}
	}
}