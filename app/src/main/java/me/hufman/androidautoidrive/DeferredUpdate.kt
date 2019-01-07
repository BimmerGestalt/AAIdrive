package me.hufman.androidautoidrive

import java.util.*

object DeferredUpdate {
	val timers = HashMap<String, Timer>()

	fun trigger(name: String, delayFunc: (() -> Long), callback: (() -> Unit), initialDelay: Long = 100) {
		if (!timers.containsKey(name)) {
			synchronized(timers) {
				if (!timers.containsKey(name)) {
					val timer = Timer(name, true)
					timers[name] = timer
					timer.schedule(DeferredUpdateTimerTask(name, timer, delayFunc, callback), initialDelay)
				}
			}
		}
	}

	fun cancel(name: String) {
		synchronized(timers) {
			timers[name]?.cancel()
			timers.remove(name)
		}
	}

	class DeferredUpdateTimerTask(val name: String, val timer: Timer, val delayFunc: () -> Long, val callback: () -> Unit): TimerTask() {
		override fun run() {
			// each time we check to see if we should run
			val delay = delayFunc()
			if (delay > 0) {
				timer.schedule(DeferredUpdateTimerTask(name, timer, delayFunc, callback), delay)
			} else {
				cancel(name)    // prepare for another thread to schedule the timer in the future
				callback()
			}
		}

	}
}