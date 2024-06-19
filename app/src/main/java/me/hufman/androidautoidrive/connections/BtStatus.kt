package me.hufman.androidautoidrive.connections

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import android.util.Log
import me.hufman.androidautoidrive.utils.getParcelableExtraCompat
import androidx.core.content.ContextCompat
import java.util.*

val BluetoothDevice.safeName: String?
	get() = try {
		this.name
	} catch (e: SecurityException) {
		null
	}
val BluetoothDevice.safeUuids: Array<ParcelUuid?>?
	get() = try {
		this.uuids
	} catch (e: SecurityException) {
		null
	}
fun BluetoothDevice?.isCar(): Boolean {
	return this?.safeName?.startsWith("BMW") == true || this?.safeName?.startsWith("MINI") == true
}
fun BluetoothDevice.safeFetchUuidsWithSdp() {
	try {
		this.fetchUuidsWithSdp()
	} catch (_: SecurityException) { }
}
val BluetoothProfile.safeConnectedDevices: List<BluetoothDevice>
	get() {
		return try {
			this.connectedDevices
		} catch (e: SecurityException) {
			// missing BLUETOOTH_CONNECT permission
			emptyList()
		} catch (e: IllegalStateException) {
			// not connected, maybe
			emptyList()
		}
	}

class BtStatus(val context: Context, val callback: () -> Unit) {
	companion object {
		const val TAG = "BtStatus"
		val UUID_SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
	}

	private var subscribed = false

	// the resulting state
	val isHfConnected
		get() = hfListener.profile?.safeConnectedDevices?.any { it.isCar() } == true
	private val hfListener = ProfileListener(BluetoothProfile.HEADSET)

	val isA2dpConnected
		get() = a2dpListener.profile?.safeConnectedDevices?.any { it.isCar() } == true
	private val a2dpListener = ProfileListener(BluetoothProfile.A2DP)

	val isSPPAvailable
		get() = (a2dpListener.profile?.safeConnectedDevices?.filter { it.isCar() } ?: listOf()).any { device ->
			device.safeUuids?.any {
				it?.uuid == UUID_SPP
			} ?: false
		}

	val isBTConnected
		get() = isHfConnected || isA2dpConnected

	val carBrand: String?
		get() = a2dpListener.profile?.safeConnectedDevices?.filter { it.isCar() }?.map {
			when {
				it.safeName?.startsWith("BMW") == true -> "BMW"
				it.safeName?.startsWith("MINI") == true -> "MINI"
				else -> null
			}
		}?.firstOrNull()

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
			fetchUuidsWithSdp()
			callback()
		}

		fun fetchUuidsWithSdp() {
			val cars = profile?.safeConnectedDevices?.filter { it.isCar() } ?: listOf()
			cars.forEach {
				it.safeFetchUuidsWithSdp()
			}
		}
	}

	// listeners of any updates
	private val bluetoothListener = object: BroadcastReceiver() {
		override fun onReceive(p0: Context?, intent: Intent?) {
			if (intent?.action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED &&
					intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_CONNECTED) {
				val device = intent.getParcelableExtraCompat(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
				device?.safeFetchUuidsWithSdp()
			}
			callback()
		}
	}

	fun fetchUuidsWithSdp() {
		a2dpListener.fetchUuidsWithSdp()
	}

	fun register() {
		Log.i(TAG, "Starting to watch for Bluetooth connection")
		if (subscribed) {
			return
		}
		context.getSystemService(BluetoothManager::class.java).adapter?.apply {
			this.getProfileProxy(context, hfListener, BluetoothProfile.HEADSET)
			this.getProfileProxy(context, a2dpListener, BluetoothProfile.A2DP)
		}
		val btFilter = IntentFilter().apply {
			addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
			addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
			addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
		}
		ContextCompat.registerReceiver(context, bluetoothListener, btFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
		subscribed = true
	}

	fun unregister() {
		try {
			subscribed = false
			context.unregisterReceiver(bluetoothListener)
		} catch (e: IllegalArgumentException) {}
		context.getSystemService(BluetoothManager::class.java).adapter?.apply {
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