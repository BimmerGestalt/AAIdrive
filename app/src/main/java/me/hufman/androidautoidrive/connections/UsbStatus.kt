package me.hufman.androidautoidrive.connections

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED

class UsbStatus(val context: Context, val callback: () -> Unit) {

	private var subscribed = false

	val isUsbConnected
		get() = usbListener.connectedProfiles["connected"] == true
	val isUsbTransferConnected
		get() = usbListener.connectedProfiles["mtp"] == true
	val isUsbAccessoryConnected
		get() = usbListener.isBMWConnected()

	/**
	 * Listen to system USB announcements
	 * Constants defined in https://android.googlesource.com/platform/frameworks/base/+/6d319b8a/core/java/android/hardware/usb/UsbManager.java#61
	 */
	private val usbListener = object: BroadcastReceiver() {
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

		var manager: UsbManager? = null
		var connectedProfiles: Map<String, Boolean> = mapOf()

		fun subscribe(manager: UsbManager) {
			this.manager = manager
			ContextCompat.registerReceiver(context, this, IntentFilter(UsbManager.ACTION_USB_ACCESSORY_ATTACHED), RECEIVER_NOT_EXPORTED)
			ContextCompat.registerReceiver(context, this, IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED), RECEIVER_NOT_EXPORTED)
			ContextCompat.registerReceiver(context, this, IntentFilter(ACTION_USB_STATE), RECEIVER_NOT_EXPORTED)
		}

		fun unsubscribe() {
			try {
				context.unregisterReceiver(this)
			} catch (e: IllegalArgumentException) {}
		}

		fun isBMWConnected(): Boolean {
			val accessories = manager?.accessoryList ?: return false
			return accessories.any {
				it.manufacturer.contains("BMW")
			}
		}

		override fun onReceive(context: Context?, intent: Intent?) {
			intent ?: return
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
	}

	fun register() {
		if (!subscribed) {
			usbListener.subscribe(context.getSystemService(UsbManager::class.java))
			subscribed = true
		}
	}

	fun unregister() {
		if (subscribed) {
			subscribed = false
			usbListener.unsubscribe()
		}
	}
}