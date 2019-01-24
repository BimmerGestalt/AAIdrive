package me.hufman.androidautoidrive.carapp

import android.util.Log
import me.hufman.androidautoidrive.Utils
import me.hufman.idriveconnectionkit.rhmi.RHMIAction
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import me.hufman.idriveconnectionkit.rhmi.RHMIModel

const val TAG = "InputState"

/** Handles letter entry from the car's input widget */
class InputState<T:Any>(val inputComponent: RHMIComponent.Input, val onEntry: (String) -> Unit, val onSelect: (T, Int) -> Unit) {
	var input = ""
	var suggestions: MutableList<T> = ArrayList()

	init {
		inputComponent.getAction()?.asRAAction()?.rhmiActionCallback = object:RHMIAction.RHMIActionCallback {
			override fun onActionEvent(args: Map<*, *>?) {
				val letter = args?.get(8.toByte()) as? String ?: return
				Log.i(TAG, "Received speller input $letter")
				when (letter) {
					"delall" -> input = ""
					"del" -> input = input.dropLast(1)
					else -> input += letter
				}
				inputComponent.getResultModel()?.asRaDataModel()?.value = input
				onEntry(input)
			}
		}
		inputComponent.getSuggestAction()?.asRAAction()?.rhmiActionCallback = object:RHMIAction.RHMIActionCallback {
			override fun onActionEvent(args: Map<*, *>?) {
				val data = args?.get(1.toByte()) ?: return
				val index = Utils.etchAsInt(data)
				val suggestion = suggestions.getOrNull(index)
				if (suggestion == null) {
					Log.w(TAG, "Car selected input suggestion $index which was not found in the list of suggestions")
				} else {
					onSelect(suggestion, index)
				}
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