package me.hufman.androidautoidrive.phoneui

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.util.Log
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import java.util.*

fun BluetoothDevice.isCar(): Boolean {
	return this.name.startsWith("BMW") || this.name.startsWith("MINI")
}

/**
 * Assists in determining prerequisites and difficulties in the car connection
 */
class CarConnectionDebugging(val context: Context) {
	companion object {
		const val TAG = "CarDebugging"
		val UUID_SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
		const val BCL_REPORT_TIMEOUT = 1000
		const val SESSION_INIT_TIMEOUT = 1000
	}

	val securityAccess = SecurityAccess.getInstance(context)
	val idriveListener = IDriveConnectionListener()

	val isConnectedInstalled
		get() = securityAccess.installedSecurityServices.isNotEmpty()

	val isConnectedSecurityConnected
		get() = securityAccess.isConnected()

	val isBMWConnectedInstalled
		get() = securityAccess.installedSecurityServices.any {
			it.name.startsWith("BMW")
		}

	val isMiniConnectedInstalled
		get() = securityAccess.installedSecurityServices.any {
			it.name.startsWith("Mini")
		}

	inner class ProfileListener(val profileId: Int): BluetoothProfile.ServiceListener {
		var profile: BluetoothProfile? = null
		val profileName = when(profileId) {
			BluetoothProfile.HEADSET -> "hf"
			BluetoothProfile.A2DP -> "a2dp"
			else -> "Profile#$profileId"
		}

		override fun onServiceDisconnected(p0: Int)
		{
			profile = null
			Log.d(TAG, "$profileName is unloaded")
			redraw()
		}
		override fun onServiceConnected(p0: Int, profile: BluetoothProfile?) {
			this.profile = profile
			Log.d(TAG, "$profileName is loaded")
			val cars = profile?.connectedDevices?.filter { it.isCar() } ?: listOf()
			cars.forEach {
				uuidListener.discover(it)
			}
			redraw()
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
			redraw()
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
			return device.uuids.any {
				(it)?.uuid == UUID_SPP
			}
		}

		override fun onReceive(p0: Context?, intent: Intent?) {
			Log.d(TAG, "Received notification of BT discovery: ${intent?.action}")
			if (intent?.action == BluetoothAdapter.ACTION_DISCOVERY_FINISHED) {
//				a2dpListener.profile?.connectedDevices?.filter { it.isCar() }?.forEach {
//					discover(it)
//				}
			}
			if (intent?.action == BluetoothDevice.ACTION_UUID) {
				val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
				Log.d(TAG, "Found BT endpoints on ${device.name}")
				if (device.isCar()) {
					val uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID) ?: return
					uuids.forEach {
						Log.d(TAG, "  - $it")
					}
					redraw()
				}
			}
		}
	}

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
			context.registerReceiver(this, IntentFilter(UsbManager.ACTION_USB_ACCESSORY_ATTACHED))
			context.registerReceiver(this, IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED))
			context.registerReceiver(this, IntentFilter(ACTION_USB_STATE))
		}

		fun unsubscribe() {
			context.unregisterReceiver(this)
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
				redraw()
			}
			if (intent.action == UsbManager.ACTION_USB_ACCESSORY_DETACHED) {
				redraw()
			}
			// the phone's USB connection has changed
			if (intent.action == ACTION_USB_STATE) {
				val connectedProfiles = KNOWN_PROFILES.filter { intent.hasExtra(it) }.associateWith {
					intent.getBooleanExtra(it, false)
				}
				this.connectedProfiles = connectedProfiles
				Log.i(TAG, "Received notification of USB state, connected usb profiles: $connectedProfiles")
				redraw()
			}
		}
	}

	private val bclListener = BclStatusListener {
		// need to watch for if we are stuck in SESSION_INIT_BYTES_SEND
		// which indicates whether BT Apps is enabled in the car
		// we don't need to do a redraw here, because SetupActivity is doing it itself
	}

	private val mguProber = MGUProber {
		redraw()
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

	// if the BCL tunnel has started
	val isBCLConnecting
		get() = bclListener.state != "UNKNOWN" && bclListener.state != "DETACHED" && bclListener.staleness < BCL_REPORT_TIMEOUT

	// indicates that SESSION_INIT is failing, and the Car's Apps setting is not enabled
	val isBCLStuck
		get() = bclListener.state == "SESSION_INIT_BYTES_SEND" && bclListener.stateAge > SESSION_INIT_TIMEOUT

	val isBCLConnected
		get() = idriveListener.isConnected

	val bclTransport
		get() = bclListener.transport

	val mguDetected
		get() = mguProber.connectedPorts.isNotEmpty()

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
		usbListener.subscribe(context.getSystemService(UsbManager::class.java))
		bclListener.subscribe(context)
		mguProber.start()
	}

	fun unregister() {
		context.unregisterReceiver(bluetoothListener)
		context.unregisterReceiver(uuidListener)
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
		usbListener.unsubscribe()
		bclListener.unsubscribe(context)
		mguProber.quitSafely()
	}

	fun redraw() {
		context.sendBroadcast(Intent(SetupActivity.INTENT_REDRAW))
	}
}