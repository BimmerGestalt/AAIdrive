package me.hufman.androidautoidrive

import android.os.Handler
import android.os.Looper
import android.util.Log
import de.bmw.idrive.BMWRemoting
import me.hufman.idriveconnectionkit.android.IDriveConnectionObserver
import java.lang.IllegalStateException
import java.lang.RuntimeException

const val TAG = "CarThread"
/**
 * A thread subclass that swallows errors when the car disconnects
 * It also sets up an Android Looper
 */
class CarThread(name: String, val runnable: () -> (Unit)): Thread(name) {
	var handler: Handler? = null
	val iDriveConnectionObserver = IDriveConnectionObserver()

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
		} catch (e: IllegalStateException) {
			// posted to a dead handler
			Log.i(TAG, "Shutting down thread $name due to IllegalStateException: $e", e)
		} catch (e: RuntimeException) {
			// phone was unplugged during an RPC command
			Log.i(TAG, "Shutting down thread $name due to RuntimeException: $e", e)
		} catch (e: org.apache.etch.util.TimeoutException) {
			// phone was unplugged during an RPC command
			Log.i(TAG, "Shutting down thread $name due to Etch TimeoutException")
		} catch (e: BMWRemoting.ServiceException) {
			if (!iDriveConnectionObserver.isConnected) {
				// the car is no longer connected
				// so this is most likely a crash caused by the closed connection
				Log.i(TAG, "Shutting down thread $name due to disconnection")
			} else {
				throw(e)
			}
		}
	}

	fun post(block: () -> Unit) {
		handler?.post(block)
	}

	fun quit() {
		handler?.looper?.quit()
		handler = null      // no longer useful
	}

	fun quitSafely() {
		handler?.looper?.quitSafely()
		handler = null      // no longer useful
	}
}