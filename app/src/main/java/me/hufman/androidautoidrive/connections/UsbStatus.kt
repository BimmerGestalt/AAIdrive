package me.hufman.androidautoidrive.connections

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.util.Log
import me.hufman.androidautoidrive.BroadcastReceiver

class UsbStatus(val context: Context, val callback: () -> Unit) {
	val isUsbConnected
		get() = connectedProfiles["connected"] == true
	val isUsbTransferConnected
		get() = connectedProfiles["mtp"] == true
	val isUsbAccessoryConnected
		get() = isBMWConnected()

	fun isBMWConnected(): Boolean {
		val accessories = manager?.accessoryList ?: return false
		return accessories.any {
			it.manufacturer.contains("BMW")
		}
	}

	/**
	 * Listen to system USB announcements
	 * Constants defined in https://android.googlesource.com/platform/frameworks/base/+/6d319b8a/core/java/android/hardware/usb/UsbManager.java#61
	 */
	val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"  // private action about connected state
	val KNOWN_PROFILES = listOf(
			"connected",
			"host_connected",
			"configured",
			"unlocked",
			"none",
			"adb",
			"rndis",
			"mtp",
			"ptp",
			"audio_source",
			"midi",
			"accessory",
			"ncm"
	)

	private var manager: UsbManager? = null
	private var connectedProfiles: Map<String, Boolean> = mapOf()

	val receiver = BroadcastReceiver { _, intent ->
		// the phone has connected to a new usb, isBMWConnected may be different now
		if (intent.action == UsbManager.ACTION_USB_ACCESSORY_ATTACHED) {
			callback()
		}
		if (intent.action == UsbManager.ACTION_USB_ACCESSORY_DETACHED) {
			callback()
		}
		// the phone's USB connection has changed
		if (intent.action == ACTION_USB_STATE) {
			val connectedProfiles = KNOWN_PROFILES.filter { intent.hasExtra(it) }.associateWith {
				intent.getBooleanExtra(it, false)
			}
			this.connectedProfiles = connectedProfiles
			Log.i(CarConnectionDebugging.TAG, "Received notification of USB state, connected usb profiles: $connectedProfiles")
			callback()
		}
	}

	fun register() {
		this.manager = context.getSystemService(UsbManager::class.java)
		context.registerReceiver(receiver, IntentFilter(UsbManager.ACTION_USB_ACCESSORY_ATTACHED))
		context.registerReceiver(receiver, IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED))
		context.registerReceiver(receiver, IntentFilter(ACTION_USB_STATE))
	}

	fun unregister() {
		context.unregisterReceiver(receiver)
	}
}