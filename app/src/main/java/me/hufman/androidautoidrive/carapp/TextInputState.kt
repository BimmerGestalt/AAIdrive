package me.hufman.androidautoidrive.carapp

import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.idriveconnectionkit.rhmi.RHMIState

interface TextInputController {
	fun getSuggestions(draft: String): List<CharSequence>
	fun onSelect(selection: String)
}

class TextInputState(listStateId: Int, inputState: RHMIState, val controller: TextInputController): InputState<CharSequence>(inputState) {
	init {
		inputComponent.getSuggestAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = listStateId
	}
	override fun onEntry(input: String) {
		sendSuggestions(controller.getSuggestions(input))
	}

	override fun onSelect(item: CharSequence, index: Int) {
		if (item.isNotEmpty()) {
			controller.onSelect(item.toString())
		}
		onInput("delall")
	}

	override fun convertRow(row: CharSequence): String {
		return UnicodeCleaner.clean(row.toString())
	}
}