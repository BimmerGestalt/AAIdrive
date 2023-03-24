package me.hufman.androidautoidrive

import android.os.Handler
import android.os.Looper
import android.util.Log
import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionObserver
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.job
import kotlin.coroutines.CoroutineContext

const val TAG = "CarThread"
/**
 * A thread subclass that swallows errors when the car disconnects
 * It also sets up an Android Looper
 */
class CarThread(name: String, var runnable: () -> (Unit)): Thread(name) {
	var handler: Handler? = null
	val iDriveConnectionObserver = IDriveConnectionObserver()

	init {
		isDaemon = true
	}

	override fun run() {
		try {
			Looper.prepare()
			handler = Handler(Looper.myLooper()!!)
			runnable()
			runnable = {}
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
			} else if (e.errorMsg?.contains("RHMI application was already connected") == true) {
				// sometimes, the BCL tunnel blips during the start of the connection
				// and so previously-initialized apps are still "in the car" though the tunnel has since restarted
				// and so the car complains that the app is already connected
				// so shut down the thread for now and wait for MainService to start this app module again
				Log.i(TAG, "RHMI application was already connected, perhaps from a previous partial connection, shutting down thread $name")
			} else {
				throw(e)
			}
		} finally {
			// if we fail during init, make sure to forget the runnable
			runnable = {}
		}
	}

	fun post(block: () -> Unit) {
		if (handler?.looper?.thread?.isAlive == true) {
			handler?.post(block)
		}
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

var CarThreadExceptionHandler = CoroutineExceptionHandler { c: CoroutineContext, e: Throwable ->
	when (e) {
		is IllegalStateException -> {
			// posted to a dead handler
			Log.i(TAG, "Shutting down coroutine thread $c due to IllegalStateException: $e", e)
		}
		is RuntimeException -> {
			// phone was unplugged during an RPC command
			Log.i(TAG, "Shutting down coroutine thread $c due to RuntimeException: $e", e)
		}
		is org.apache.etch.util.TimeoutException -> {
			// phone was unplugged during an RPC command
			Log.i(TAG, "Shutting down coroutine thread $c due to Etch TimeoutException")
		}
		is BMWRemoting.ServiceException -> {
			// probably phone was unplugged during an RPC command
			Log.i(TAG, "Shutting down coroutine thread $c due to ServiceException: $e", e)
		}
		else -> throw e
	}
}