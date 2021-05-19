package me.hufman.androidautoidrive.carapp.notifications.views

import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.carapp.InputState
import me.hufman.androidautoidrive.carapp.notifications.ReplyController
import me.hufman.idriveconnectionkit.rhmi.RHMIState

class ReplyView(destState: RHMIState, inputState: RHMIState, val replyController: ReplyController): InputState<CharSequence>(inputState) {
	// only send once
	var sent = false
	init {
		inputComponent.getSuggestAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = destState.id
	}
	override fun onEntry(input: String) {
		if (input == "") {
			sendSuggestions(replyController.getSuggestions(input))
		} else {
			sendSuggestions(listOf(input) + replyController.getSuggestions(input))
		}
	}

	override fun onSelect(item: CharSequence, index: Int) {
		if (!sent) {
			if (item.isNotEmpty()) {
				replyController.sendReply(item.toString())
			}
			sent = true
		}
	}

	override fun convertRow(row: CharSequence): String {
		return UnicodeCleaner.clean(row.toString())
	}
}