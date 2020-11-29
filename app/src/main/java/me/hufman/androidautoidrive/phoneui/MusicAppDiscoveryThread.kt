package me.hufman.androidautoidrive.phoneui

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import me.hufman.androidautoidrive.music.MusicAppDiscovery


class MusicAppDiscoveryThread(val context: Context, val callback: (MusicAppDiscovery) -> Unit): HandlerThread("MusicAppDiscovery UI") {
	private var handler: Handler? = null
	var discovery: MusicAppDiscovery? = null
		private set

	override fun onLooperPrepared() {
		val handler = Handler(this.looper)
		this.handler = handler
		val discovery = MusicAppDiscovery(context, handler)
		this.discovery = discovery
		discovery.listener = Runnable {
			scheduleRedraw()
		}
		discovery.discoverApps()
		discovery.probeApps(false)
	}

	private val redrawRunnable = Runnable {
		val discovery = discovery
		if (discovery != null) {
			callback(discovery)
		}
	}

	private fun scheduleRedraw() {
		val handler = handler ?: return
		handler.removeCallbacks(redrawRunnable)
		handler.postDelayed(redrawRunnable, 100)
	}

	fun discovery() {
		val handler = handler ?: return
		handler.post {
			discovery?.discoverApps()
		}
	}

	fun forceDiscovery() {
		val handler = handler ?: return
		handler.post {
			discovery?.cancelDiscovery()
			discovery?.discoverApps()
			discovery?.probeApps(true)
		}
	}

	fun stopDiscovery() {
		discovery?.cancelDiscovery()
		quitSafely()
	}
}