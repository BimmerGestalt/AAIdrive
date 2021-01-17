package me.hufman.androidautoidrive.connections

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import java.lang.IllegalArgumentException
import java.util.*

fun BluetoothDevice.isCar(): Boolean {
	return this.name.startsWith("BMW") || this.name.startsWith("MINI")
}

class BtStatus(val context: Context, val callback: () -> Unit) {
	companion object {
		const val TAG = "BtStatus"
		val UUID_SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
	}

	// the resulting state
	val isHfConnected
		get() = hfListener.profile?.connectedDevices?.any { it.isCar() } == true
	private val hfListener = ProfileListener(BluetoothProfile.HEADSET)

	val isA2dpConnected
		get() = a2dpListener.profile?.connectedDevices?.any { it.isCar() } == true
	private val a2dpListener = ProfileListener(BluetoothProfile.A2DP)

	val isSPPAvailable
		get() = (a2dpListener.profile?.connectedDevices?.filter { it.isCar() } ?: listOf()).any {
			uuidListener.isSPPAvailable(it)
		}

	val isBTConnected
		get() = isHfConnected || isA2dpConnected

	inner class ProfileListener(profileId: Int): BluetoothProfile.ServiceListener {
		var profile: BluetoothProfile? = null
		private val profileName = when(profileId) {
			BluetoothProfile.HEADSET -> "hf"
			BluetoothProfile.A2DP -> "a2dp"
			else -> "Profile#$profileId"
		}

		override fun onServiceDisconnected(p0: Int)
		{
			profile = null
			Log.d(TAG, "$profileName is unloaded")
			callback()
		}
		override fun onServiceConnected(p0: Int, profile: BluetoothProfile?) {
			this.profile = profile
			Log.d(TAG, "$profileName is loaded")
			val cars = profile?.connectedDevices?.filter { it.isCar() } ?: listOf()
			cars.forEach {
				uuidListener.discover(it)
			}
			callback()
		}
	}

	// listeners of any updates
	private val bluetoothListener = object: BroadcastReceiver() {
		override fun onReceive(p0: Context?, intent: Intent?) {
			if (intent?.action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED &&
					intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_CONNECTED) {
				val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
				uuidListener.discover(device)
			}
			callback()
		}
	}

	/**
	 * Listen for any SDP updates, to watch for the SPP device (Bluetooth BCL connection) showing up
	 */
	private val uuidListener = object: BroadcastReceiver() {
		fun discover(device: BluetoothDevice) {
			val successfulSdp = device.fetchUuidsWithSdp()
			Log.d(TAG, "Triggering a discovery: $successfulSdp")
		}

		fun isSPPAvailable(device: BluetoothDevice): Boolean {
			return device.uuids?.any {
				(it)?.uuid == UUID_SPP
			} ?: false
		}

		override fun onReceive(p0: Context?, intent: Intent?) {
			Log.d(TAG, "Received notification of BT discovery: ${intent?.action}")
			if (intent?.action == BluetoothDevice.ACTION_UUID) {
				val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
				Log.d(TAG, "Found BT endpoints on ${device.name}")
				if (device.isCar()) {
					val uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID) ?: return
					uuids.forEach {
						Log.d(TAG, "  - $it")
					}
					callback()
				}
			}
		}
	}

	fun register() {
		Log.i(TAG, "Starting to watch for Bluetooth connection")
		BluetoothAdapter.getDefaultAdapter()?.apply {
			this.getProfileProxy(context, hfListener, BluetoothProfile.HEADSET)
			this.getProfileProxy(context, a2dpListener, BluetoothProfile.A2DP)
		}
		val btFilter = IntentFilter().apply {
			addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
			addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
			addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
		}
		val uuidFilter = IntentFilter().apply {
			addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
			addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
			addAction(BluetoothDevice.ACTION_UUID)
		}
		context.registerReceiver(bluetoothListener, btFilter)
		context.registerReceiver(uuidListener, uuidFilter)
	}

	fun unregister() {
		try {
			context.unregisterReceiver(bluetoothListener)
		} catch (e: IllegalArgumentException) {}
		try {
			context.unregisterReceiver(uuidListener)
		} catch (e: IllegalArgumentException) {}
		BluetoothAdapter.getDefaultAdapter()?.apply {
			val hfProfile = hfListener.profile
			if (hfProfile != null) {
				this.closeProfileProxy(BluetoothProfile.HEADSET, hfProfile)
			}
			val a2dpProfile = a2dpListener.profile
			if (a2dpProfile != null) {
				this.closeProfileProxy(BluetoothProfile.A2DP, a2dpProfile)
			}
		}
	}
}