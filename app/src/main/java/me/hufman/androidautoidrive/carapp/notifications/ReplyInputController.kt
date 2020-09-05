package me.hufman.androidautoidrive.carapp.notifications

import me.hufman.androidautoidrive.carapp.TextInputController
import me.hufman.androidautoidrive.notifications.CarNotification
import me.hufman.androidautoidrive.notifications.CarNotificationController


class ReplyInputController(val notification: CarNotification, val action: CarNotification.Action, val controller: CarNotificationController): TextInputController {
	override fun getSuggestions(draft: String): List<CharSequence> {
		return if (draft == "") {
			action.suggestedReplies
		} else {
			listOf(draft)
		}
	}

	override fun onSelect(selection: String) {
		controller.reply(notification, action, selection)
	}
}