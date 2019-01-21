package me.hufman.androidautoidrive

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.RuntimeException

const val TAG = "CarThread"
/**
 * A thread subclass that swallows errors when the car disconnects
 * It also sets up an Android Looper
 */
class CarThread(name: String, val runnable: () -> (Unit)): Thread(name) {
	var handler: Handler? = null

	init {
		isDaemon = true
	}

	override fun run() {
		try {
			Looper.prepare()
			handler = Handler(Looper.myLooper())
			runnable()
			Log.i(TAG, "Successfully finished runnable for thread $name, starting Handler loop")
			Looper.loop()
			Log.i(TAG, "Successfully finished tasks for thread $name")
		} catch (e: RuntimeException) {
			// phone was unplugged during an RPC command
			Log.i(TAG, "Shutting down thread $name due to RuntimeException")
		} catch (e: org.apache.etch.util.TimeoutException) {
			// phone was unplugged during an RPC command
			Log.i(TAG, "Shutting down thread $name due to Etch TimeoutException")
		}
	}
}