package me.hufman.androidautoidrive.carapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import androidx.core.content.ContextCompat
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIApplication
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIEvent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIModel
import me.hufman.androidautoidrive.carapp.notifications.TAG

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

interface ReadoutCommands {
	fun readout(name: String, lines: Iterable<String>)
	fun cancel(name: String)
}

/**
 * Implements a high-level wrapper around the car's Readout system
 * A client would instantiate a ReadoutController with a given name and command interface
 * and can trigger readout and cancel commands
 * The client should also forward onTTSEvents to update the isActive flag,
 * and the isActive flag shows that the car is currently reading out this named ReadoutController
 */
class ReadoutController(val name: String, val readoutCommands: ReadoutCommands) {
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
//		Log.d(TAG, "Starting readout from $name")
		readoutCommands.readout(name, lines)
	}

	fun cancel() {
		if (!isActive) {
			return
		}
//		Log.d(TAG, "Cancelling $name readout")
		readoutCommands.cancel(name)
	}
}

class ReadoutCommandsRHMI(val speechEvent: RHMIEvent.ActionEvent, val commandEvent: RHMIEvent.ActionEvent): ReadoutCommands {
	companion object {
		fun build(app: RHMIApplication): ReadoutCommandsRHMI {
			val events = app.events.values.filterIsInstance<RHMIEvent.ActionEvent>().filter {
				it.getAction()?.asLinkAction()?.actionType == "readout"
			}
			if (events.size != 2) {
				throw IllegalArgumentException("UI Description is missing 2 readout events")
			}
			return ReadoutCommandsRHMI(events[0], events[1])
		}
	}

	val speechList = speechEvent.getAction()?.asLinkAction()?.getLinkModel()?.asRaListModel()!!
	val commandList = commandEvent.getAction()?.asLinkAction()?.getLinkModel()?.asRaListModel()!!

	override fun readout(name: String, lines: Iterable<String>) {
		val data = RHMIModel.RaListModel.RHMIListConcrete(2)
		data.addRow(arrayOf(lines.joinToString(".\n"), name))
		Log.d(TAG, "Starting rhmi readout from $name: ${data[0][0]}")
		speechList.value = data
		speechEvent.triggerEvent()
	}

	override fun cancel(name: String) {
		Log.d(TAG, "Cancelling rhmi readout from $name")
		val data = RHMIModel.RaListModel.RHMIListConcrete(2)
		data.addRow(arrayOf("STR_READOUT_STOP", name))
		commandList.value = data
		commandEvent.triggerEvent()
	}
}

class ReadoutCommandsSender(val context: Context): ReadoutCommands {
	override fun readout(name: String, lines: Iterable<String>) {
		val intent = Intent(ReadoutCommandsReceiver.INTENT_READOUT)
			.setPackage(context.packageName)
			.putExtra(ReadoutCommandsReceiver.EXTRA_COMMAND, ReadoutCommandsReceiver.EXTRA_COMMAND_LINES)
			.putExtra(ReadoutCommandsReceiver.EXTRA_COMMAND_NAME, name)
			.putExtra(ReadoutCommandsReceiver.EXTRA_COMMAND_LINES, lines.toList().toTypedArray())
		context.sendBroadcast(intent)
	}

	override fun cancel(name: String) {
		val intent = Intent(ReadoutCommandsReceiver.INTENT_READOUT)
			.setPackage(context.packageName)
			.putExtra(ReadoutCommandsReceiver.EXTRA_COMMAND, ReadoutCommandsReceiver.EXTRA_COMMAND_CANCEL)
			.putExtra(ReadoutCommandsReceiver.EXTRA_COMMAND_NAME, name)
		context.sendBroadcast(intent)
	}
}

class ReadoutCommandsReceiver(val commands: ReadoutCommands): BroadcastReceiver() {
	companion object {
		const val INTENT_READOUT = "me.hufman.androidautoidrive.READOUT_COMMAND"
		const val EXTRA_COMMAND = "READOUT"
		const val EXTRA_COMMAND_NAME = "READOUT_NAME"
		const val EXTRA_COMMAND_LINES = "READOUT_LINES"
		const val EXTRA_COMMAND_CANCEL = "READOUT_CANCEL"
	}
	override fun onReceive(context: Context?, intent: Intent?) {
		intent ?: return
		val command = intent.getStringExtra(EXTRA_COMMAND)
		val name = intent.getStringExtra(EXTRA_COMMAND_NAME) ?: return
		val lines = intent.getStringArrayExtra(EXTRA_COMMAND_LINES)
		if (command == EXTRA_COMMAND_LINES && lines != null) {
			commands.readout(name, lines.toList())
		}
		if (command == EXTRA_COMMAND_CANCEL) {
			commands.cancel(name)
		}
	}

	fun register(context: Context, handler: Handler) {
		ContextCompat.registerReceiver(context, this, IntentFilter(INTENT_READOUT), null, handler, ContextCompat.RECEIVER_NOT_EXPORTED)
	}

	fun unregister(context: Context) {
		try {
			context.unregisterReceiver(this)
		} catch (e: IllegalArgumentException) {
			// duplicate unregister
		}
	}

}