package me.hufman.androidautoidrive.carapp.notifications


interface ReplyController {
	fun getSuggestions(draft: String): List<CharSequence>
	fun sendReply(reply: String)
}

class ReplyControllerNotification(val notification: CarNotification, val action: CarNotification.Action, val controller: CarNotificationController): ReplyController {

	override fun getSuggestions(draft: String): List<CharSequence> {
		return action.suggestedReplies
	}

	override fun sendReply(reply: String) {
		controller.reply(notification, action, reply)
	}
}