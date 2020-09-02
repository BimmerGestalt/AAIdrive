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

	/** Replaces any supported unicode from this string to the shortname tag */
	fun clean(input: String): String {
		return EmojiParser.parseToAliases(input, EmojiParser.FitzpatrickAction.REMOVE)
	}

	/** Builds just enough of an Emoji object to pass unit tests, and adds it to the data */
	@VisibleForTesting(otherwise = VisibleForTesting.NONE)
	fun _addPlaceholderEmoji(emoji: String, aliases: List<String>, description: String) {
		val emojiData = EmojiManager.getAll() as ArrayList<Emoji>
		emojiData.add(Emoji(emojiChar=emoji, emoji=emoji, unicode=emoji, aliases=aliases, description = description, htmlDec="", htmlHex=""))
	}
}