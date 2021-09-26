package me.hufman.androidautoidrive

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

class ApplicationCallbacks: Application(), Application.ActivityLifecycleCallbacks {
	companion object {
		// track whether we are in the foreground
		val visibleWindows = AtomicInteger(0)

		fun onResume() {
			visibleWindows.incrementAndGet()
		}
		fun onPause() {
			visibleWindows.decrementAndGet()
		}
	}

	override fun onCreate() {
		super.onCreate()

		registerActivityLifecycleCallbacks(this)
	}

	override fun onActivityPaused(p0: Activity) {
		onPause()
	}

	override fun onActivityResumed(p0: Activity) {
		onResume()
	}

	override fun onActivityStarted(p0: Activity) {}

	override fun onActivityDestroyed(p0: Activity) {}

	override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {}

	override fun onActivityStopped(p0: Activity) {}

	override fun onActivityCreated(p0: Activity, p1: Bundle?) {}
}