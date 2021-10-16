package me.hufman.androidautoidrive

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper

/**
 * Utility class to determine whether the phone has ever suspended or killed the app in the background
 *
 * The client should
 */
class BackgroundInterruptionDetection(val preferences: SharedPreferences, val handler: Handler,
                                      val ttl: Long = DEFAULT_TTL, val timeProvider: () -> Long = {System.currentTimeMillis()}) {
	companion object {
		const val BACKGROUND_PERSISTENCE_NAME = "BackgroundInterruptionDetection.json"
		const val DEFAULT_TTL = 10_000L      // 10 seconds TTL
		const val MINIMUM_RUNNING_TIME = 300_000L    // stay alive for a length of time before clearing error counters

		fun build(context: Context, handler: Handler? = null,
		          ttl: Long = DEFAULT_TTL, timeProvider: () -> Long = {System.currentTimeMillis()}): BackgroundInterruptionDetection {
			return BackgroundInterruptionDetection(
					context.getSharedPreferences(BACKGROUND_PERSISTENCE_NAME, MODE_PRIVATE),
					handler ?: Handler(Looper.getMainLooper()),
					ttl, timeProvider
			)
		}
	}

	val startTime = timeProvider()
	var lastTimeAlive: Long = 0
	var isAlive: Boolean = false
	var detectedSuspended = 0
	var detectedKilled = 0

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
	}

	private fun saveState() {
		with(preferences.edit()) {
			putLong("lastTimeAlive", lastTimeAlive)
			putInt("detectedSuspended", detectedSuspended)
			putInt("detectedKilled", detectedKilled)
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
			detectedKilled ++
			saveState()
		}
	}

	fun start() {
		pollLoop()
	}

	/**
	 * Immediately cancel any Handler callbacks
	 */
	fun stop() {
		handler.removeCallbacks(pollRunnable)
	}

	/**
	 * Remember that we correctly shut down, as opposed to being killed
	 */
	fun safelyStop() {
		handler.removeCallbacks(pollRunnable)
		val runningTime = timeProvider() - startTime
		lastTimeAlive = 0
		if (runningTime > MINIMUM_RUNNING_TIME && detectedSuspended == startedSuspended) {
			detectedSuspended = 0
		}
		if (runningTime > MINIMUM_RUNNING_TIME && detectedKilled == startedKilled) {
			detectedKilled = 0
		}
		saveState()
	}
}