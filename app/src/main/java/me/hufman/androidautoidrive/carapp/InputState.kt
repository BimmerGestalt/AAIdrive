package me.hufman.androidautoidrive.carapp

import android.util.Log
import me.hufman.idriveconnectionkit.rhmi.*

const val TAG = "InputState"

/** Handles letter entry from the car's input widget */
class InputState<T:Any>(val inputComponent: RHMIComponent.Input, val onEntry: (String) -> Unit, val onSelect: (T, Int) -> Unit) {
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
	}

	fun sendSuggestions(newSuggestions: List<T>) {
		synchronized(this) {
			suggestions.clear()
			suggestions.addAll(newSuggestions)
		}
		val outputListModel = inputComponent.getSuggestModel() ?: return
		val outputList = RHMIModel.RaListModel.RHMIListConcrete(1)
		newSuggestions.forEach { outputList.addRow(arrayOf(it.toString())) }
		outputListModel.setValue(outputList, 0, outputList.height, outputList.height)
	}
}