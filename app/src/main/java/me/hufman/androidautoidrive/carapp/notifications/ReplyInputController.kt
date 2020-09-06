package me.hufman.androidautoidrive.carapp.notifications

import me.hufman.androidautoidrive.notifications.CarNotification
import me.hufman.androidautoidrive.notifications.CarNotificationController


class ReplyInputController(val notification: CarNotification, val action: CarNotification.Action, val controller: CarNotificationController, autocomplete: SuggestionStrategy): SuggestionInputController(autocomplete) {
	override fun getSuggestions(draft: String, currentWord: String): List<CharSequence> {
		return if (draft == "" && currentWord == "") {
			action.suggestedReplies
		} else {
			super.getSuggestions(draft, currentWord)
		}
	}

	override fun onSelect(selection: String) {
		controller.reply(notification, action, selection)
	}
}