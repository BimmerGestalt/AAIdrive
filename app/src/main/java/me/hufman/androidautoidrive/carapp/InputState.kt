package me.hufman.androidautoidrive.carapp

import android.util.Log
import me.hufman.idriveconnectionkit.rhmi.*

const val TAG = "InputState"

/** Handles letter entry from the car's input widget */
abstract class InputState<T:Any>(val inputComponent: RHMIComponent.Input) {
	var input = ""
	var suggestions: MutableList<T> = ArrayList()

	init {
		inputComponent.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionSpellerCallback { letter ->
			Log.i(TAG, "Received speller input $letter")
			when (letter) {
				"delall" -> input = ""
				"del" -> input = input.dropLast(1)
				else -> input += letter
			}
			inputComponent.getResultModel()?.asRaDataModel()?.value = input
			onEntry(input)
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

	fun sendSuggestions(newSuggestions: List<T>) {
		synchronized(this) {
			suggestions.clear()
			suggestions.addAll(newSuggestions)
		}
		val outputListModel = inputComponent.getSuggestModel() ?: return
		val outputList = RHMIModel.RaListModel.RHMIListConcrete(1)
		newSuggestions.forEach { outputList.addRow(arrayOf(convertRow(it))) }
		outputListModel.setValue(outputList, 0, outputList.height, outputList.height)
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