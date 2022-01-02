package me.hufman.androidautoidrive.carapp.notifications.views

import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.carapp.InputState
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.notifications.ReplyController

class ReplyView(destState: RHMIState, inputState: RHMIState, val replyController: ReplyController): InputState<CharSequence>(inputState) {
	// track whether the last input was from dictation
	var voicedInput = false
	// only send once
	var sent = false
	init {
		// when picking a result from the suggestion side view
		inputComponent.getSuggestAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = destState.id
		// when pushing OK
		inputComponent.getResultAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = destState.id
	}

	override fun onInput(letter: String) {
		// the user dictated a response, ignore the next onOK()
		voicedInput = input == "" && letter.length > 1
		super.onInput(letter)
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

	override fun onOk() {
		if (!voicedInput && input.isNotEmpty()) {
			replyController.sendReply(input)
		} else {
			voicedInput = false     // the car pushes Ok once after a voiced input, allow the user to push Ok from now on
			throw RHMIActionAbort()
		}
	}

	override fun convertRow(row: CharSequence): String {
		return UnicodeCleaner.clean(row.toString(), false)
	}
}