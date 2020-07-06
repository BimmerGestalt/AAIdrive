package me.hufman.androidautoidrive

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class A2DPBroadcastReceiverKt: BroadcastReceiver() {
	val states = mapOf(
			BluetoothProfile.STATE_DISCONNECTED to "disconnected",
			BluetoothProfile.STATE_CONNECTING to "connecting",
			BluetoothProfile.STATE_CONNECTED to "connected",
			BluetoothProfile.STATE_DISCONNECTING to "disconnecting"
	).withDefault { "unknown" }
	override fun onReceive(p0: Context?, p1: Intent?) {
		if (p1?.action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
			val device = p1.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
			val oldState = states[p1.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1)]
			val newState = states[p1.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)]
			Log.i("A2DPBroadcast", "Notified of A2DP status: ${device?.name} $oldState -> $newState")
		}
		if (p1?.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
			val device = p1.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
			Log.i("A2DPBroadcast", "Notified of ACL connection: ${device?.name}")
		}
		if (p1?.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
			val device = p1.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
			Log.i("A2DPBroadcast", "Notified of ACL disconnection: ${device?.name}")
		}
	}
}