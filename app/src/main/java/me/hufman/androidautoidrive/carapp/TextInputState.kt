package me.hufman.androidautoidrive.carapp

import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.idriveconnectionkit.rhmi.RHMIState
import java.util.*

interface TextInputController {
	fun getSuggestions(draft: String, currentWord: String): List<CharSequence>
	fun onSelect(selection: String)
}

class TextInputState(nextStateId: Int, inputState: RHMIState, val controller: TextInputController): InputState<CharSequence>(inputState) {
	val phraseSegments = LinkedList<CharSequence>() // the pieces of completion that should be deleted atomically
	var currentSegment = StringBuilder()

	init {
		inputComponent.getSuggestAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = nextStateId
	}

	override fun onInput(letter: String) {
		if (letter == "delall") {
			input = ""
			phraseSegments.clear()
			currentSegment.clear()
		} else if (letter == "del") {
			if (currentSegment.isNotEmpty()) {
				currentSegment.deleteCharAt(currentSegment.length - 1)
			} else if (phraseSegments.isNotEmpty()) {
				phraseSegments.removeAt(phraseSegments.size - 1)
			}
		} else if (letter.length == 1) {
			if (letter == " ") {
				phraseSegments.add(currentSegment.toString())
				currentSegment.clear()
			} else {
				currentSegment.append(letter)
			}
		} else if (letter.length > 1) {
			// spoken text
			if (currentSegment.isNotEmpty()) {
				phraseSegments.add(currentSegment.toString())
				currentSegment.clear()
			}
			phraseSegments.add(letter)
		}

		// update the InputState with the current input
		input = if (currentSegment.isEmpty()) {
			phraseSegments.joinToString(" ")
		} else if (phraseSegments.isNotEmpty()) {
			phraseSegments.joinToString(" ") + " " + currentSegment
		} else {
			currentSegment.toString()
		}

		inputComponent.getResultModel()?.asRaDataModel()?.value = input
		onEntry(input)
	}

	override fun onEntry(input: String) {
		val currentInput = if (input.isNotBlank()) listOf(input) else emptyList()
		sendSuggestions(currentInput + controller.getSuggestions(phraseSegments.joinToString(" "), currentSegment.toString()))
	}

	override fun onSelect(item: CharSequence, index: Int) {
		if (item == input) {    // the user selected the finished input
			controller.onSelect(item.toString())
		} else if (input.isBlank()) {    // the user selected an initial suggestion
			controller.onSelect(item.toString())
		} else {        // the user selected a word completion
			currentSegment.clear()
			phraseSegments.add(item)
			onInput("") // update the input selections
			throw RHMIActionAbort() // don't close the window
		}
	}

	override fun convertRow(row: CharSequence): String {
		return UnicodeCleaner.clean(row.toString())
	}
}