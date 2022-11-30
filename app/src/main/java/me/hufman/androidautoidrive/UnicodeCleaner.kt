package me.hufman.androidautoidrive

import android.content.Context
import androidx.annotation.VisibleForTesting
import io.wax911.emojify.EmojiManager
import io.wax911.emojify.model.Emoji
import io.wax911.emojify.parser.EmojiParser

/** Cleans a text string to be suitable for showing in the car */
object UnicodeCleaner {
	/** Loads the list of emoji from a local asset file in the Context */
	fun init(context: Context) {
		try {
			EmojiManager.initEmojiData(context)
		} catch (e: Exception) {
			// can't find emoji data in context?
		}
	}

	/** Replaces any shortname tag to the matching emoji */
	fun encode(input: String): String {
		return EmojiParser.parseToUnicode(input)
	}

	/** Finds the first strong directionality class from this input */
	fun getStrongDirectionality(input: String): Byte? {
		return input.asSequence().map {
			Character.getDirectionality(it)
		}.map {
			when (it) {
				// ignore the embedding/override markers, but coalesce RTL Arabic to a strong RTL
				Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> Character.DIRECTIONALITY_RIGHT_TO_LEFT
				else -> it
			}
		}.firstOrNull {
			it == Character.DIRECTIONALITY_LEFT_TO_RIGHT ||
			it == Character.DIRECTIONALITY_RIGHT_TO_LEFT
		}
	}

	fun cleanBidiIsolates(input: String): String {
		val LRE = '\u202A'      // LTR embed
		val RLE = '\u202B'      // RTL embed
		val PDF = '\u202C'      // end embed
		val LRI = '\u2066'      // LTR isolate
		val RLI = '\u2067'      // RTL isolate
		val FSI = '\u2068'      // auto-ordered isolate
		val PDI = '\u2069'      // end isolate
		val ISOLATES = charArrayOf(LRI, RLI, FSI)
		var cleaned = input

		// clean out isolate symbols
		var lastIsolatePos = cleaned.lastIndexOfAny(ISOLATES)
		while (lastIsolatePos >= 0) {
			val isolatePop = cleaned.indexOf(PDI, lastIsolatePos)
			if (lastIsolatePos < cleaned.length && isolatePop > lastIsolatePos) {
				val isolate = cleaned[lastIsolatePos]
				val before = cleaned.substring(0, lastIsolatePos)
				val subsection = cleaned.substring(lastIsolatePos + 1, isolatePop)
				val after = cleaned.substring(isolatePop + 1)
				val directionality by lazy { getStrongDirectionality(subsection) }
				val newEmbed = if (isolate == LRI) {
					"$LRE$subsection$PDF"
				} else if (isolate == RLI) {
					"$RLE$subsection$PDF"
				} else if (directionality == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
					"$LRE$subsection$PDF"
				} else if (directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT) {
					"$RLE$subsection$PDF"
				} else {
					subsection      // just a bare string
				}
				cleaned = before + newEmbed + after
			}
			lastIsolatePos = cleaned.lastIndexOfAny(ISOLATES)
		}
		return cleaned
	}

	// referenced from https://demos.joypixels.com/latest/ascii-smileys.html
	// with supplement from https://en.wikipedia.org/wiki/List_of_emoticons
	val EMOTICONS = mapOf(
			"joy" to "XD",
			"smiley" to ":D",
			"slight_smile" to ":)",
			"sweat_smile" to "':D",
			"laughing" to "XD",
			"wink" to ";)",
			"sweat" to "':(",
			"kissing_heart" to ":*",
			"stuck_out_tongue_winking_eye" to "XP",
			"disappointed" to ":(",
			"angry" to ">:(",
			"cry" to ":'(",
			"fearful" to "D:",
			"flushed" to ":$",
			"dizzy_face" to "%)",
			"innocent" to "O:)",
			"sunglasses" to "8)",
			"expressionless" to "-__-",
			"confused" to ":\\",
			"stuck_out_tongue" to ":P",
			"open_mouth" to ":O",
			"no_mouth" to ":X",
			// and some others from Emojify's data
			"grinning" to ":D",
			"smile" to ":D",
			"blush" to ":$",
			"relaxed" to ":)",
			"kissing_closed_eyes" to ":*",
			"kissing" to ":*",
			"kissing_smiling_eyes" to ":*",
			"stuck_out_tongue_closed_eyes" to "XP",
			"grin" to ":D",
			"unamused" to "-__-",
			"disappointed" to "v_v",
			"yum" to "XP",
			"astonished" to ":O",
			"frowning" to "D:",
			"anguished" to "D:",
			"smiling_imp" to ">:)",
			"grimacing" to ":E",
			"neutral_face" to ":|",
			"no_mouth" to ":X",
			// other symbols
			"yellow_heart" to "♥",
			"blue_heart" to "♥",
			"purple_heart" to "♥",
			"green_heart" to "♥",
			"heart" to "♥",
			"broken_heart" to "</3",
			"heartpulse" to "♥",
			"heartbeat" to "♥",
			"sparkling_heart" to "♥",
			"cupid" to "♥",
			"hearts" to "♥",
			"black_heart" to "♥",
			"orange_heart" to "♥",
			"diamonds" to "♦",
			"large_orange_diamond" to "♦",
			"large_blue_diamond" to "♦",
			"small_orange_diamond" to "♦",
			"small_blue_diamond" to "♦",
			"clubs" to "♣",
			"spades" to "♠",
	)

	fun convertEmoticon(alias: String): String {
		return EMOTICONS[alias] ?: alias
	}

	/** Replaces any supported unicode from this string to the shortname tag */
	fun clean(input: String, convertEmoticons: Boolean = true): String {
		val bidiCleaned = cleanBidiIsolates(input)

		val emojiTransformer = object : EmojiParser.EmojiTransformer {
			override fun transform(unicodeCandidate: EmojiParser.UnicodeCandidate): String {
				val emoji = unicodeCandidate.emoji?.emoji ?: ""
				if (emoji[0].code < 255) {
					// base ascii code, like :copyright: and :registered:
					return emoji
				} else if (convertEmoticons && EMOTICONS.containsKey(unicodeCandidate.emoji?.aliases?.get(0) ?: "")) {
					return " " + convertEmoticon(unicodeCandidate.emoji?.aliases?.get(0) ?: "") + " "
				} else {
					return ":" + unicodeCandidate.emoji?.aliases?.get(0) + ":"
				}
			}
		}
		return EmojiParser.parseFromUnicode(bidiCleaned, emojiTransformer)
	}

	/** Builds a simple Emoji object */
	@VisibleForTesting(otherwise = VisibleForTesting.NONE)
	fun _buildPlaceholderEmoji(emoji: String, aliases: List<String>, description: String): Emoji {
		return Emoji(emojiChar=emoji, emoji=emoji, unicode=emoji, aliases=aliases, description = description,
				htmlDec="&#${emoji[0].code};", htmlHex="&#${emoji[0].code.toString(16)};")
	}

	/** Builds just enough of an Emoji object to pass unit tests, and adds it to the data */
	@VisibleForTesting(otherwise = VisibleForTesting.NONE)
	fun _addPlaceholderEmoji(emoji: String, aliases: List<String>, description: String) {
		val emojiData = EmojiManager.getAll() as ArrayList<Emoji>
		emojiData.add(_buildPlaceholderEmoji(emoji, aliases, description))
	}

	/** Using a specific emoji alias search, return matching emoji */
	fun searchEmoji(emojiList: Collection<Emoji>, search: String, limit: Int = 10): List<Emoji> {
		if (search.isBlank()) return emptyList()
		val startMatches = emojiList.asSequence()
				.filter {  emoji ->
					emoji.aliases?.any { alias ->
						alias.startsWith(search, true)
					} ?: false
				}
		val subMatches = emojiList.asSequence()
				.filter { emoji ->
					emoji.aliases?.any { alias ->
						alias.contains("-$search", true) ||
						alias.contains("_$search", true)
					} ?: false
				}
		return (startMatches + subMatches)
				.distinct()
				.take(limit)
				.toList()
	}
}