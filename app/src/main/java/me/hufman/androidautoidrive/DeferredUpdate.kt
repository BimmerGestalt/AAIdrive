package me.hufman.androidautoidrive

import android.os.Handler
import kotlin.math.max

/**
 * Represents a deferrable-able runnable
 */
class DeferredUpdate(val handler: Handler, val currentTimeProvider: () -> Long = {System.currentTimeMillis()}) {
	var deferredTime = 0L
	var task: Runnable? = null

	/**
	 * Enqueue this runnable to be executed, if this hasn't been deferred yet
	 */
	fun trigger(initialDelay: Long = 100, callback: (() -> Unit)) {
		cancel()
		synchronized(this) {
			this.task = DeferredUpdateTimerTask(callback)
		}
		schedule(initialDelay)
	}

	/**
	 * Defer any execution by at least this long
	 */
	fun defer(delay: Long) {
		deferredTime = currentTimeProvider() + delay
		schedule(0)
	}

	private fun schedule(delay: Long) {
		synchronized(this) {
			val task = task
			if (task != null) {
				val remainingTime = max(0, max(delay, deferredTime - currentTimeProvider()))
				handler.removeCallbacks(task)
				if (remainingTime == 0L && handler.looper.isCurrentThread) {
					task.run()
				} else {
					handler.postDelayed(task, remainingTime)
				}
			}
		}
	}

	fun cancel() {
		synchronized(this) {
			if (task != null) {
				handler.removeCallbacks(task)
			}
			task = null
		}
	}

	inner class DeferredUpdateTimerTask(val callback: () -> Unit): Runnable {
		override fun run() {
			cancel()    // prepare for another thread to schedule the timer in the future
			callback()
		}

	}
}