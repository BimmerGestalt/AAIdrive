package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_connection_status.*
import me.hufman.androidautoidrive.CarInformationObserver
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.connections.CarConnectionDebugging
import me.hufman.androidautoidrive.phoneui.showEither
import me.hufman.androidautoidrive.phoneui.visible
import java.util.*

class ConnectionStatusFragment: Fragment() {
	val connectionDebugging by lazy {
		CarConnectionDebugging(requireContext()) { activity?.runOnUiThread { redraw() } }
	}
	val carInformationObserver = CarInformationObserver {
		activity?.runOnUiThread { redraw() }
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_connection_status, container, false)
	}

	override fun onResume() {
		super.onResume()

		connectionDebugging.register()
		redraw()
	}

	override fun onPause() {
		connectionDebugging.unregister()
		super.onPause()
	}

	fun redraw() {
		if (!isResumed) return
		val deviceName = Settings.Global.getString(requireContext().contentResolver, "device_name")

		// Bluetooth connection status
		showEither(paneBTDisconnected, paneBTConnected) { connectionDebugging.isBTConnected }
		showEither(paneA2dpDisconnected, paneA2dpConnected, { connectionDebugging.isBTConnected }) {
			connectionDebugging.isA2dpConnected
		}
		showEither(paneSppDisconnected, paneSppConnected, { connectionDebugging.isBTConnected }) {
			connectionDebugging.isSPPAvailable
		}

		// USB connection status
		paneUSBDisconnected.visible = !connectionDebugging.isUsbConnected
		paneUSBConnected.visible = connectionDebugging.isUsbConnected && !connectionDebugging.isUsbTransferConnected && !connectionDebugging.isUsbAccessoryConnected
		paneUSBMTPConnected.visible = connectionDebugging.isUsbConnected && connectionDebugging.isUsbTransferConnected && !connectionDebugging.isUsbAccessoryConnected
		paneUSBACCConnected.visible = connectionDebugging.isUsbAccessoryConnected
		txtEnableUsbMtp.visible = !connectionDebugging.isBCLConnecting && !connectionDebugging.isBCLConnected
		txtEnableUsbAcc.visible = !connectionDebugging.isBCLConnecting && !connectionDebugging.isBCLConnected
		txtEnableUsbAcc.text = getString(R.string.txt_setup_enable_usbacc, deviceName)

		// apps connection is running, perhaps on a transport
		paneBclDisconnected.visible = (connectionDebugging.isSPPAvailable || connectionDebugging.isUsbAccessoryConnected) &&
				!connectionDebugging.isBCLConnecting && !connectionDebugging.isBCLConnected
		paneBclConnecting.visible = connectionDebugging.isBCLConnecting && !connectionDebugging.isBCLConnected
		paneBclStuck.visible = connectionDebugging.isBCLStuck
		txtEnableBcl.text = getString(R.string.txt_setup_enable_bcl_mode, deviceName)
		paneBclConnected.visible = connectionDebugging.isBCLConnected
		txtBclConnected.text = if (connectionDebugging.bclTransport == null)
			getString(R.string.txt_setup_bcl_connected)
		else
			getString(R.string.txt_setup_bcl_connected_transport, connectionDebugging.bclTransport)

		paneCarConnected.visible = connectionDebugging.idriveListener.isConnected
		val chassisCode = ChassisCode.fromCode(carInformationObserver.capabilities["vehicle.type"] ?: "Unknown")
		txtCarConnected.text = if (chassisCode != null) {
			resources.getString(R.string.notification_description_chassiscode, chassisCode.toString())
		} else {
			when (connectionDebugging.idriveListener.brand?.toLowerCase(Locale.ROOT)) {
				"bmw" -> resources.getString(R.string.notification_description_bmw)
				"mini" -> resources.getString(R.string.notification_description_mini)
				else -> resources.getString(R.string.notification_description)
			}
		}
	}
}