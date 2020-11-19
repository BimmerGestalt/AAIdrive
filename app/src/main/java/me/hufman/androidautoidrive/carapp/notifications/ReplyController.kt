package me.hufman.androidautoidrive.carapp.notifications

import io.wax911.emojify.EmojiManager
import me.hufman.androidautoidrive.UnicodeCleaner.searchEmoji
import me.hufman.androidautoidrive.notifications.CarNotification
import me.hufman.androidautoidrive.notifications.CarNotificationController

interface ReplyController {
	fun getSuggestions(draft: String): List<CharSequence>
	fun sendReply(reply: String)
}

class ReplyControllerNotification(val notification: CarNotification, val action: CarNotification.Action, val controller: CarNotificationController): ReplyController {
	fun getEmojiSuggestions(draft: String): List<CharSequence> {
		val prefix = draft.substringBeforeLast(':')
		val emojiSearch = draft.substringAfterLast(':', "")
		println("Searching for emoji with $emojiSearch")
		return searchEmoji(EmojiManager.getAll(), emojiSearch).map {
			(prefix + it.unicode)
		}
	}

	override fun getSuggestions(draft: String): List<CharSequence> {
		return if (draft.isBlank()) {
			action.suggestedReplies
		} else {
			getEmojiSuggestions(draft)
		}
	}

	override fun sendReply(reply: String) {
		controller.reply(notification.key, action.name.toString(), reply)
	}
}