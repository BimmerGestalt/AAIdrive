package me.hufman.androidautoidrive.connections

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.text.format.DateUtils
import java.text.NumberFormat

class BclStatusListener(val context: Context, val callback: () -> Unit = {}): BroadcastReceiver() {
	companion object {
		const val BCL_REPORT = "com.bmwgroup.connected.accessory.ACTION_CAR_ACCESSORY_INFO"
		const val BCL_TRANSPORT = "com.bmwgroup.connected.accessory.ACTION_CAR_ACCESSORY_TRANSPORT_SWITCH"
	}

	val stringBuilder = StringBuilder()
	val numberFormatter: NumberFormat = NumberFormat.getNumberInstance()

	var subscribed = false

	var lastUpdate: Long = -1
	var initTimestamp: Long = -1
	var bytesRead: Long = 0
	var bytesWritten: Long = 0
	var numConnections: Int = 0
	var instanceId: Short = 0
	var watchdogRtt: Long = -1
	var huBufsize: Int = 0
	var remainingAckBytes: Long = 0
	var state: String? = "UNKNOWN"
	var stateUpdate: Long = -1  // when the state was last updated
	var brand: String? = null
	var transport: String? = null   // could be any of ETH,BT,USB

	val staleness
		get() = SystemClock.uptimeMillis() - lastUpdate
	val sessionAge
		get() = SystemClock.uptimeMillis() - initTimestamp
	val stateAge
		get() = SystemClock.uptimeMillis() - stateUpdate

	fun subscribe() {
		if (!subscribed) {
			context.registerReceiver(this, IntentFilter(BCL_REPORT))
			context.registerReceiver(this, IntentFilter(BCL_TRANSPORT))
			subscribed = true
		}
	}
	fun unsubscribe() {
		try {
			subscribed = false
			context.unregisterReceiver(this)
		} catch (e: IllegalArgumentException) {}
	}

	override fun onReceive(context: Context?, intent: Intent?) {
		context ?: return
		intent ?: return

		if (intent.action == BCL_REPORT) {
			lastUpdate = SystemClock.uptimeMillis()
			initTimestamp = intent.getLongExtra("EXTRA_START_TIMESTAMP", -1)
			bytesRead = intent.getLongExtra("EXTRA_NUM_BYTES_READ", 0)
			bytesWritten = intent.getLongExtra("EXTRA_NUM_BYTES_WRITTEN", 0)
			numConnections = intent.getIntExtra("EXTRA_NUM_CONNECTIONS", 0)
			instanceId = intent.getShortExtra("EXTRA_INSTANCE_ID", 0)
			watchdogRtt = intent.getLongExtra("EXTRA_WATCHDOG_RTT", -1)
			huBufsize = intent.getIntExtra("EXTRA_HU_BUFFER_SIZE", 0)
			remainingAckBytes = intent.getLongExtra("EXTRA_REMAINING_ACK_BYTES", 0)
			val oldState = state
			state = intent.getStringExtra("EXTRA_STATE")
			brand = intent.getStringExtra("EXTRA_BRAND")
			if (oldState != state) {
				stateUpdate = lastUpdate
			}
		}
		// Only posts the transport when the connection is established
		if (intent.action == BCL_TRANSPORT) {
			transport = intent.getStringExtra("EXTRA_TRANSPORT")
		}

		callback()
	}

	override fun toString(): String {
		val age = DateUtils.formatElapsedTime(stringBuilder,this.sessionAge / 1000)
		return arrayOf(
				"Brand: $brand",
				"State: $state",
				"Instance ID: $instanceId",
				"Session Age: $age",
				"Num Connections: $numConnections",
				"Buffer Size: $huBufsize",
				"Bytes Read: ${numberFormatter.format(bytesRead)}",
				"Bytes Written: ${numberFormatter.format(bytesWritten)}",
				"Remaining Ack Bytes: $remainingAckBytes",
				"Watchdog RTT: $watchdogRtt ms"
		).joinToString("\n")
	}
}