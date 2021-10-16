package me.hufman.androidautoidrive.carapp.notifications.views

import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.carapp.InputState
import me.hufman.androidautoidrive.carapp.notifications.ReplyController

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
		return UnicodeCleaner.clean(row.toString(), false)
	}
}