package me.hufman.androidautoidrive.connections

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionObserver
import io.bimmergestalt.idriveconnectkit.android.security.KnownSecurityServices
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import me.hufman.androidautoidrive.carapp.music.MusicAppMode

/**
 * Assists in determining prerequisites and difficulties in the car connection
 */
class CarConnectionDebugging(val context: Context, val callback: () -> Unit) {
	companion object {
		const val TAG = "CarDebugging"
		const val SESSION_INIT_TIMEOUT = 1000
		const val BCL_REPORT_TIMEOUT = 1000
		const val BCL_REDRAW_DEBOUNCE = 100
	}

	val deviceName = Settings.Global.getString(context.contentResolver, "device_name")

	private val securityAccess = SecurityAccess.getInstance(context).also {
		it.callback = callback
	}

	private val idriveListener = IDriveConnectionObserver { callback() }

	val isConnectedSecurityInstalled
		get() = SecurityAccess.installedSecurityServices.isNotEmpty()

	val isConnectedSecurityConnecting
		get() = securityAccess.isConnecting()

	val isConnectedSecurityConnected
		get() = securityAccess.isConnected()

	val isBMWInstalled = SecurityAccess.installedSecurityServices.any {
		it.name.startsWith("BMW")
	}

	val isMiniInstalled
		get() = SecurityAccess.installedSecurityServices.any {
			it.name.startsWith("Mini")
		}

	val isBMWConnectedInstalled = SecurityAccess.installedSecurityServices.any {
			it.name.startsWith("BMWC")
		}

	val isMiniConnectedInstalled
		get() = SecurityAccess.installedSecurityServices.any {
			it.name.startsWith("MiniC")
		}

	val isBMWConnected65Installed: Boolean
		get() = try {
			SecurityAccess.installedSecurityServices.filter {
				it.name.startsWith("BMWC")
			}.any {
				val version = context.packageManager.getPackageInfo(it.packageName, 0).versionName
				version.startsWith("6.5")
			}
		} catch (e: Exception) { false }

	val isMiniConnected65Installed: Boolean
		get() = try {
			SecurityAccess.installedSecurityServices.filter {
				it.name.startsWith("MiniC")
			}.any {
				val version = context.packageManager.getPackageInfo(it.packageName, 0).versionName
				version.startsWith("6.5")
			}
		} catch (e: Exception) { false }

	val isBMWMineInstalled
		get() = SecurityAccess.installedSecurityServices.contains(KnownSecurityServices.BMWMine) ||
				SecurityAccess.installedSecurityServices.contains(KnownSecurityServices.BMWMineNA)
	val isMiniMineInstalled
		get() = SecurityAccess.installedSecurityServices.contains(KnownSecurityServices.MiniMine) ||
				SecurityAccess.installedSecurityServices.contains(KnownSecurityServices.MiniMineNA)

	private val btStatus = BtStatus(context) { callback() }
	private val usbStatus = UsbStatus(context) { callback() }

	private var bclNextRedraw: Long = 0
	private val bclListener = BclStatusListener(context) {
		// need to watch for if we are stuck in SESSION_INIT_BYTES_SEND
		// which indicates whether BT Apps is enabled in the car
		if (bclNextRedraw < SystemClock.uptimeMillis()) {
			callback()
			bclNextRedraw = SystemClock.uptimeMillis() + BCL_REDRAW_DEBOUNCE
		}
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
		get() = bclListener.transport ?: MusicAppMode.TRANSPORT_PORTS.fromPort(idriveListener.port)?.toString()

	val carBrand
		get() = idriveListener.brand

	val btCarBrand
		get() = btStatus.carBrand

	fun register() {
		idriveListener.callback = { callback() }
		btStatus.register()
		usbStatus.register()
		bclListener.subscribe()
	}

	fun _registerBtStatus() {
		btStatus.register()
	}
	fun _registerUsbStatus() {
		usbStatus.register()
	}

	fun probeSecurityModules() {
		securityAccess.connect()
	}

	fun unregister() {
		idriveListener.callback = {}
		btStatus.unregister()
		usbStatus.unregister()
		bclListener.unsubscribe()
	}
}