package me.hufman.androidautoidrive

import android.content.Context
import android.support.annotation.VisibleForTesting
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

	/** Replaces any supported unicode from this string to the shortname tag */
	fun clean(input: String): String {
		return EmojiParser.parseToAliases(input, EmojiParser.FitzpatrickAction.REMOVE)
	}

	/** Builds a simple Emoji object */
	@VisibleForTesting(otherwise = VisibleForTesting.NONE)
	fun _buildPlaceholderEmoji(emoji: String, aliases: List<String>, description: String): Emoji {
		return Emoji(emojiChar=emoji, emoji=emoji, unicode=emoji, aliases=aliases, description = description,
				htmlDec="&#${emoji[0].toLong()};", htmlHex="&#${emoji[0].toLong().toString(16)};")
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