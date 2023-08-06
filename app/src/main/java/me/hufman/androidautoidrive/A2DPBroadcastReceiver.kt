package me.hufman.androidautoidrive

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.SparseArray
import me.hufman.androidautoidrive.connections.isCar
import me.hufman.androidautoidrive.connections.safeName
import me.hufman.androidautoidrive.utils.getParcelableExtraCompat

class A2DPBroadcastReceiver: BroadcastReceiver() {
	val TAG = "A2DPBroadcast"
	val states = SparseArray<String>(4).apply {
		put(BluetoothProfile.STATE_DISCONNECTED, "disconnected")
		put(BluetoothProfile.STATE_CONNECTING, "connecting")
		put(BluetoothProfile.STATE_CONNECTED, "connected")
		put(BluetoothProfile.STATE_DISCONNECTING, "disconnecting")
	}

	override fun onReceive(context: Context?, intent: Intent?) {
		context ?: return
		intent ?: return
		if (intent.action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
			val device = intent.getParcelableExtraCompat(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
			val oldState = states[intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1)] ?: "unknown"
			val newStateCode = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
			val newState = states[intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)] ?: "unknown"
			Log.i(TAG, "Notified of A2DP status: ${device?.safeName} $oldState -> $newState")
			if (newStateCode == BluetoothProfile.STATE_CONNECTED && isValidDevice(device)) {
				startMainService(context)
			}
		}
		if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
			val device = intent.getParcelableExtraCompat(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
			Log.i(TAG, "Notified of ACL connection: ${device?.safeName}")
			if (isValidDevice(device)) {
				startMainService(context)
			}
		}
		if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
			val device = intent.getParcelableExtraCompat(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
			Log.i(TAG, "Notified of ACL disconnection: ${device?.safeName}")
		}
	}

	fun isValidDevice(device: BluetoothDevice?): Boolean {
		return device.isCar()
	}

	fun startMainService(context: Context) {
		Log.i(TAG, "Starting the service because of a Bluetooth car connection")
		val intent = Intent(context, MainService::class.java)
				.setAction(MainService.ACTION_START)
				.putExtra(MainService.EXTRA_FOREGROUND, true)

		// if we are in a background mode, we need to startForegroundService
		try {
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
				context.startForegroundService(intent)
			} else {
				context.startService(intent)
			}
		} catch (e: Exception) {
			Log.w(TAG, "Failed to start MainService", e)
		}
	}
}