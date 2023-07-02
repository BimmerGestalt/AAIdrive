package me.hufman.androidautoidrive

import android.content.*
import android.content.Context.MODE_PRIVATE
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionReceiver.Companion.INTENT_DETACHED

/**
 * Utility class to determine whether the phone has ever suspended or killed the app in the background
 *
 * The client should
 */
class BackgroundInterruptionDetection(val preferences: SharedPreferences, val handler: Handler, val context: Context,
                                      val ttl: Long = DEFAULT_TTL, val timeProvider: () -> Long = {System.currentTimeMillis()}) {
	companion object {
		const val TAG = "BackgroundInterruption"
		const val BACKGROUND_PERSISTENCE_NAME = "BackgroundInterruptionDetection.json"
		const val DEFAULT_TTL = 10_000L      // 10 seconds TTL
		const val MINIMUM_RUNNING_TIME = 300_000L    // stay alive for a length of time before clearing error counters
		const val ACTION_REPORT_DISCONNECT = "me.hufman.androidautoidrive.MainService.reportUnexpectedDisconnect"
		const val UNEXPECTED_TIME_THRESHOLD = 30_000L    // only seem concerned about tunnel disconnects if we didn't receive an announced disconnect in the previous 30s

		fun build(context: Context, handler: Handler? = null,
		          ttl: Long = DEFAULT_TTL, timeProvider: () -> Long = {System.currentTimeMillis()}): BackgroundInterruptionDetection {
			return BackgroundInterruptionDetection(
					context.getSharedPreferences(BACKGROUND_PERSISTENCE_NAME, MODE_PRIVATE),
					handler ?: Handler(Looper.getMainLooper()), context,
					ttl, timeProvider
			)
		}

		fun reportUnexpectedDisconnect(context: Context) {
			context.sendBroadcast(Intent(ACTION_REPORT_DISCONNECT)
					.setPackage(BuildConfig.APPLICATION_ID))
		}
	}

	val startTime = timeProvider()
	var lastTimeAlive: Long = 0
	var isAlive: Boolean = false
	var detectedSuspended = 0
	var detectedKilled = 0
	var lastAnnouncedDisconnect: Long = 0
	var lastUnexpectedDisconnect: Long = 0
	var detectedTunnelKilled = 0
	var detectedTunnelKilledThisSession = 0

	// the initially loaded values, to detect if any problems happened during this run
	val startedSuspended: Int
	val startedKilled: Int

	init {
		loadState()
		startedSuspended = detectedSuspended
		startedKilled = detectedKilled
	}

	val pollRunnable = Runnable {
		pollLoop()
	}

	private fun pollLoop() {
		declareStillAlive()
		handler.removeCallbacks(pollRunnable)
		handler.postDelayed(pollRunnable, ttl*2/5)
	}

	private fun declareStillAlive() {
		val currentTime = timeProvider()
		if (lastTimeAlive > 0 && currentTime - lastTimeAlive > ttl) {
			detectedSuspended++
		}
		lastTimeAlive = currentTime
		saveState()
	}

	private fun loadState() {
		lastTimeAlive = preferences.getLong("lastTimeAlive", 0)
		detectedSuspended = preferences.getInt("detectedSuspended", 0)
		detectedKilled = preferences.getInt("detectedKilled", 0)
		detectedTunnelKilled = preferences.getInt("detectedTunnelKilled", 0)
	}

	private fun saveState() {
		with(preferences.edit()) {
			putLong("lastTimeAlive", lastTimeAlive)
			putInt("detectedSuspended", detectedSuspended)
			putInt("detectedKilled", detectedKilled)
			putInt("detectedTunnelKilled", detectedTunnelKilled)
			apply()
		}
	}

	/** Check if a previous app invocation was not stopped safely
	 *
	 * Relies on the lastTimeAlive not being changed, so
	 * this function must be called before start is called
	 */
	fun detectKilledPreviously() {
		if (lastTimeAlive > 0) {
			Log.w(TAG, "Detected an unexpected shutdown!")
			detectedKilled ++
			saveState()
		} else {
			Log.i(TAG, "No previous unexpected shutdown detected")
		}
	}

	fun start() {
		Log.i(TAG, "Starting to watch for unexpected shutdowns")
		pollLoop()
	}

	/**
	 * Immediately cancel any Handler callbacks
	 */
	fun stop() {
		Log.i(TAG, "Being told to stop polling")
		handler.removeCallbacks(pollRunnable)
	}

	/**
	 * Remember that we correctly shut down, as opposed to being killed
	 */
	fun safelyStop() {
		handler.removeCallbacks(pollRunnable)
		val runningTime = timeProvider() - startTime
		Log.i(TAG, "Recording a graceful shutdown, after $runningTime")
		lastTimeAlive = 0
		if (runningTime > MINIMUM_RUNNING_TIME && detectedSuspended == startedSuspended) {
			detectedSuspended = 0
		}
		if (runningTime > MINIMUM_RUNNING_TIME && detectedKilled == startedKilled) {
			detectedKilled = 0
		}
		saveState()
	}

	/** Listen for shutdown events */
	private val onAnnouncedDisconnect = object: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) { onAnnouncedDisconnect() }
	}
	fun onAnnouncedDisconnect() {
		Log.i(TAG, "Received announced disconnect")
		lastAnnouncedDisconnect = timeProvider()
		// only decrement the counter if we haven't incremented this session yet
		if (detectedTunnelKilledThisSession == 0 && detectedTunnelKilled > 0) {
			detectedTunnelKilled -= 1
		}
	}
	private val onUnexpectedDisconnect = object: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) { onUnexpectedDisconnect() }
	}
	fun onUnexpectedDisconnect() {
		if (lastUnexpectedDisconnect + UNEXPECTED_TIME_THRESHOLD < timeProvider()) {    // only increment once per interval
			Log.i(TAG, "Received unexpected disconnect")
			handler.removeCallbacks(waitForAnnouncedDisconnect)
			handler.postDelayed(waitForAnnouncedDisconnect, UNEXPECTED_TIME_THRESHOLD)
		}
		lastUnexpectedDisconnect = timeProvider()
	}

	val waitForAnnouncedDisconnect = Runnable {
		if (lastAnnouncedDisconnect + UNEXPECTED_TIME_THRESHOLD < lastUnexpectedDisconnect) {
			// didn't hear a recent Announced Disconnect
			Log.i(TAG, "No disconnect was announced!")
			detectedTunnelKilled += 1
			detectedTunnelKilledThisSession += 1
		}
	}

	fun registerListeners() {
		context.registerReceiver(onAnnouncedDisconnect, IntentFilter(INTENT_DETACHED))
		context.registerReceiver(onUnexpectedDisconnect, IntentFilter(ACTION_REPORT_DISCONNECT))
	}
	fun unregisterListeners() {
		try {
			context.unregisterReceiver(onAnnouncedDisconnect)
			context.unregisterReceiver(onUnexpectedDisconnect)
		} catch (e: IllegalArgumentException) {}
	}
}