package me.hufman.androidautoidrive.phoneui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.text.format.DateUtils
import java.lang.StringBuilder
import java.text.NumberFormat

class BclStatusListener: BroadcastReceiver() {
	companion object {
		const val BCL_REPORT = "com.bmwgroup.connected.accessory.ACTION_CAR_ACCESSORY_INFO"
	}

	val stringBuilder = StringBuilder()
	val numberFormatter = NumberFormat.getNumberInstance()

	var initTimestamp: Long = -1
	var bytesRead: Long = 0
	var bytesWritten: Long = 0
	var numConnections: Int = 0
	var instanceId: Short = 0
	var watchdogRtt: Long = -1
	var huBufsize: Int = 0
	var remainingAckBytes = 0
	var state: String? = "UNKNOWN"
	var brand: String? = null

	fun subscribe(context: Context) {
		context.registerReceiver(this, IntentFilter(BCL_REPORT))
	}
	fun unsubscribe(context: Context) {
		context.unregisterReceiver(this)
	}

	override fun onReceive(context: Context?, intent: Intent?) {
		context ?: return
		intent ?: return

		initTimestamp = intent.getLongExtra("EXTRA_START_TIMESTAMP", -1)
		bytesRead = intent.getLongExtra("EXTRA_NUM_BYTES_READ", 0)
		bytesWritten = intent.getLongExtra("EXTRA_NUM_BYTES_WRITTEN", 0)
		numConnections = intent.getIntExtra("EXTRA_NUM_CONNECTIONS", 0)
		instanceId = intent.getShortExtra("EXTRA_INSTANCE_ID", 0)
		watchdogRtt = intent.getLongExtra("EXTRA_WATCHDOG_RTT", -1)
		huBufsize = intent.getIntExtra("EXTRA_HU_BUFFER_SIZE", 0)
		remainingAckBytes = intent.getIntExtra("EXTRA_REMAINING_ACK_BYTES", 0)
		state = intent.getStringExtra("EXTRA_STATE")
		brand = intent.getStringExtra("EXTRA_BRAND")

		context.sendBroadcast(Intent(SetupActivity.INTENT_REDRAW))
	}

	override fun toString(): String {
		val age = DateUtils.formatElapsedTime(stringBuilder,(SystemClock.uptimeMillis() - initTimestamp) / 1000)
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