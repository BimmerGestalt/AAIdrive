package me.hufman.androidautoidrive.phoneui

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class MGUProber(val callback: () -> Unit): HandlerThread("MGUProber") {
	companion object {
		val MGU_ADDRESS = "172.16.222.97"
		val PORTS = listOf(8283)
		val TAG = "MGUProber"
	}

	var handler: Handler? = null
	val connectedPorts = HashSet<Int>()

	override fun onLooperPrepared() {
		handler = Handler(looper)
		schedule(1000)
	}

	val ProberTask = Runnable {
		val openPorts = HashSet<Int>()
		for (port in PORTS) {
			if (probePort(port)) {
				// we found a car! probably
				if (!connectedPorts.contains(port)) {
					Log.i(TAG, "Found open socket at $MGU_ADDRESS:$port")
				}
				openPorts.add(port)
			}
		}

		val different = connectedPorts != openPorts
		// commit to the main list
		synchronized(this) {
			connectedPorts.clear()
			connectedPorts.addAll(openPorts)
		}

		if (different) {
			callback()
		}
		schedule(4000)
	}

	fun schedule(delay: Long) {
		handler?.removeCallbacks(ProberTask)
		handler?.postDelayed(ProberTask, delay)
	}

	/**
	 * Detects whether a port is open
	 */
	private fun probePort(port: Int): Boolean {
		try {
			val address = InetSocketAddress(MGU_ADDRESS, port)
			val socket = Socket()
			socket.connect(address, 200)
			if (socket.isConnected) {
				socket.close()
				return true
			}
		}
		catch (e: IOException) {
			// this port isn't open
		}
		return false
	}
}