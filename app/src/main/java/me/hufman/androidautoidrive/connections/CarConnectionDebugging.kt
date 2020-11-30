package me.hufman.androidautoidrive.connections

import android.content.Context
import me.hufman.idriveconnectionkit.android.IDriveConnectionObserver
import me.hufman.idriveconnectionkit.android.security.SecurityAccess

/**
 * Assists in determining prerequisites and difficulties in the car connection
 */
class CarConnectionDebugging(val context: Context, val callback: () -> Unit) {
	companion object {
		const val TAG = "CarDebugging"
		const val SESSION_INIT_TIMEOUT = 1000
		const val BCL_REPORT_TIMEOUT = 1000
	}

	val securityAccess = SecurityAccess.getInstance(context)
	val idriveListener = IDriveConnectionObserver()

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

	private val btStatus = BtStatus(context) { callback() }
	private val usbStatus = UsbStatus(context) { callback() }
	private val bclListener = BclStatusListener(context) {
		// need to watch for if we are stuck in SESSION_INIT_BYTES_SEND
		// which indicates whether BT Apps is enabled in the car
		// we don't need to do a redraw here, because SetupActivity is doing it itself
	}

	// the summarized status
	val isHfConnected
		get() = btStatus.isHfConnected
	val isA2dpConnected
		get() = btStatus.isA2dpConnected
	val isSPPAvailable
		get() = btStatus.isSPPAvailable
	val isBTConnected
		get() = btStatus.isBTConnected

	val isUsbConnected
		get() = usbStatus.isUsbConnected
	val isUsbTransferConnected
		get() = usbStatus.isUsbTransferConnected
	val isUsbAccessoryConnected
		get() = usbStatus.isUsbAccessoryConnected


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

	fun register() {
		btStatus.register()
		usbStatus.register()
		bclListener.subscribe()
	}

	fun unregister() {
		btStatus.unregister()
		usbStatus.unregister()
		bclListener.unsubscribe()
	}
}