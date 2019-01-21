package me.hufman.androidautoidrive

import android.os.Handler
import android.os.Looper
import java.util.*

object DeferredUpdate {

	val tasks = HashMap<String, Runnable>()

	fun trigger(name: String, delayFunc: (() -> Long), callback: (() -> Unit), initialDelay: Long = 100) {
		schedule(name, delayFunc, callback, initialDelay)
	}

	private fun schedule(name: String, delayFunc: () -> Long, callback: () -> Unit, delay: Long) {
		synchronized(tasks) {
			val task = DeferredUpdateTimerTask(name, delayFunc, callback)
			tasks[name] = task
			Handler(Looper.myLooper()).postDelayed(task, delay)
		}
	}

	fun cancel(name: String) {
		synchronized(tasks) {
			val task = tasks[name]
			if (task != null) {
				Handler(Looper.myLooper()).removeCallbacks(task)
			}
			tasks.remove(name)
		}
	}


	class DeferredUpdateTimerTask(val name: String, val delayFunc: () -> Long, val callback: () -> Unit): Runnable {
		override fun run() {
			// each time we check to see if we should run
			val delay = delayFunc()
			if (delay > 0) {
				schedule(name, delayFunc, callback, delay)
			} else {
				cancel(name)    // prepare for another thread to schedule the timer in the future
				callback()
			}
		}

	}
}