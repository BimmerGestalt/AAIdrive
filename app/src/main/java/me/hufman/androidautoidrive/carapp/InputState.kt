package me.hufman.androidautoidrive.carapp

import android.util.Log
import me.hufman.idriveconnectionkit.rhmi.*


/** Handles letter entry from the car's input widget */
abstract class InputState<T:Any>(val state: RHMIState) {
	var input = ""
	var suggestions: MutableList<T> = ArrayList()

	val inputComponent = state.componentsList.filterIsInstance<RHMIComponent.Input>().first()

	companion object {
		const val TAG = "InputState"
		fun fits(state: RHMIState): Boolean {
			return state.componentsList.size == 1 &&
				state.componentsList[0] is RHMIComponent.Input
		}
	}

	init {
		// show any suggestions right when it shows up
		state.focusCallback = FocusCallback { focus ->
			if (focus) {
				onEntry(input)
			}
		}
		inputComponent.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionSpellerCallback { letter ->
			Log.i(TAG, "Received speller input $letter")
			onInput(letter)
		}
		inputComponent.getSuggestAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index ->
			val suggestion = suggestions.getOrNull(index)
			if (suggestion == null) {
				Log.w(TAG, "Car selected input suggestion $index which was not found in the list of suggestions")
			} else {
				onSelect(suggestion, index)
			}
		}
		inputComponent.getResultModel()?.asRaDataModel()?.value = ""
		inputComponent.getSuggestModel()?.setValue(RHMIModel.RaListModel.RHMIListConcrete(1),0,0, 0)
	}

	open fun sendSuggestions(newSuggestions: List<T>) {
		synchronized(this) {
			suggestions.clear()
			suggestions.addAll(newSuggestions)
		}
		val outputListModel = inputComponent.getSuggestModel() ?: return
		val outputList = RHMIModel.RaListModel.RHMIListConcrete(1)
		newSuggestions.forEach { outputList.addRow(arrayOf(convertRow(it))) }
		outputListModel.setValue(outputList, 0, outputList.height, outputList.height)
	}

	fun onInput(letter: String) {
		when (letter) {
			"delall" -> input = ""
			"del" -> input = input.dropLast(1)
			else -> input += letter
		}
		inputComponent.getResultModel()?.asRaDataModel()?.value = input
		onEntry(input)
	}

	/**
	 * After a user inputs a letter, or a vocal word, or a delete command
	 * This callback is called with the new complete input string
	 * This callback should call sendSuggestions() to update the list of suggestions
	 */
	abstract fun onEntry(input: String)

	/**
	 * This callback is called when the user selects a suggestion
	 */
	abstract fun onSelect(item: T, index: Int)

	/**
	 * This function converts a suggestion item to a displayable text string
	 */
	open fun convertRow(row: T): String {
		return row.toString()
	}
}