package me.hufman.androidautoidrive.carapp

import android.util.Log
import me.hufman.androidautoidrive.carapp.notifications.TAG
import me.hufman.idriveconnectionkit.rhmi.RHMIApplication
import me.hufman.idriveconnectionkit.rhmi.RHMIEvent
import me.hufman.idriveconnectionkit.rhmi.RHMIModel
import java.lang.IllegalArgumentException

data class TTSState(
	val state: Int?,
	val currentblock: Int?,
	val blocks: Int?,
	val type: String?,
	val languageavailable: Int?
)
enum class ReadoutState(val value: Int) {
	UNDEFINED(0),
	IDLE(1),
	PAUSED(2),
	ACTIVE(3),
	BUSY(4);

	companion object {
		fun fromValue(value: Int?): ReadoutState {
			return values().firstOrNull { it.value == value } ?: UNDEFINED
		}
	}
}
class ReadoutController(val name: String, val speechEvent: RHMIEvent.ActionEvent, val commandEvent: RHMIEvent.ActionEvent) {
	val speechList = speechEvent.getAction()?.asLinkAction()?.getLinkModel()?.asRaListModel()!!
	val commandList = commandEvent.getAction()?.asLinkAction()?.getLinkModel()?.asRaListModel()!!

	companion object {
		fun build(app: RHMIApplication, name: String): ReadoutController {
			val events = app.events.values.filterIsInstance<RHMIEvent.ActionEvent>().filter {
				it.getAction()?.asLinkAction()?.actionType == "readout"
			}
			if (events.size != 2) {
				throw IllegalArgumentException("UI Description is missing 2 readout events")
			}
			return ReadoutController(name, events[0], events[1])
		}
	}

	var currentState: ReadoutState = ReadoutState.UNDEFINED
	var currentName: String = ""
	var currentBlock: Int? = null
	val isActive: Boolean
		get() = currentName == name && (currentState == ReadoutState.ACTIVE || currentState == ReadoutState.BUSY)

	fun onTTSEvent(ttsState: TTSState) {
		currentState = ReadoutState.fromValue(ttsState.state)
		currentName = ttsState.type ?: ""
		currentBlock = ttsState.currentblock
		Log.d(TAG, "TTSEvent: currentState:$currentState currentName:$currentName currentBlock:$currentBlock")
	}

	fun readout(lines: Iterable<String>) {
		val data = RHMIModel.RaListModel.RHMIListConcrete(2)
		data.addRow(arrayOf(lines.joinToString(".\n"), name))
		Log.d(TAG, "Starting readout from $name: ${data[0][0]}")
		speechList.setValue(data, 0, 1, 1)
		speechEvent.triggerEvent()
	}

	fun cancel() {
		if (!isActive) {
			return
		}
		Log.d(TAG, "Cancelling $name readout")
		val data = RHMIModel.RaListModel.RHMIListConcrete(2)
		data.addRow(arrayOf("STR_READOUT_STOP", name))
		commandList.setValue(data, 0, 1, 1)
		commandEvent.triggerEvent()
	}
}