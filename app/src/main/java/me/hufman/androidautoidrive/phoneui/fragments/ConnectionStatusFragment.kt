package me.hufman.androidautoidrive.phoneui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Build
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

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		btnInstallBMW.setOnClickListener { installConnected("bmw") }
		btnInstallMini.setOnClickListener { installConnected("mini") }
		btnInstallBMWClassic.setOnClickListener { installConnectedClassic("bmw") }
		btnInstallMiniClassic.setOnClickListener { installConnectedClassic("mini") }
	}

	val isUSA
		get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			this.resources.configuration.locales.get(0).country == "US"
		} else {
			@Suppress("DEPRECATION")
			this.resources.configuration.locale.country == "US"
		}

	fun installConnected(brand: String = "bmw") {
		val packageName = if (isUSA) "de.$brand.connected.na" else "de.$brand.connected"
		val intent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
		}
		startActivity(intent)
	}

	fun installConnectedClassic(brand: String = "bmw") {
		val packageName = if (isUSA) "com.bmwgroup.connected.$brand.usa" else "com.bmwgroup.connected.$brand"
		val intent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
		}
		startActivity(intent)
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

	fun showEither(falseView: View, trueView: View, determiner: () -> Boolean) {
		showEither(falseView, trueView, {true}, determiner)
	}

	fun showEither(falseView: View, trueView: View, prereq: () -> Boolean, determiner: () -> Boolean) {
		val prereqed = prereq()
		val determination = determiner()
		falseView.visible = prereqed && !determination
		trueView.visible = prereqed && determination
	}

	fun redraw() {
		if (!isResumed) return
		val deviceName = Settings.Global.getString(requireContext().contentResolver, "device_name")

		showEither(paneBMWMissing, paneBMWReady) {
			connectionDebugging.isConnectedSecurityConnected && connectionDebugging.isBMWConnectedInstalled
		}
		btnInstallBMW.visible = !connectionDebugging.isBMWConnectedInstalled    // don't offer the button to install if it's already installed
		showEither(paneMiniMissing, paneMiniReady) {
			connectionDebugging.isConnectedSecurityConnected && connectionDebugging.isMiniConnectedInstalled
		}
		btnInstallMini.visible = !connectionDebugging.isMiniConnectedInstalled    // don't offer the button to install if it's already installed

		// if the security service isn't working for some reason, prompt to install the Classic app
		paneSecurityMissing.visible = !connectionDebugging.isConnectedSecurityConnected && connectionDebugging.isConnectedInstalled
		showEither(btnInstallMiniClassic, btnInstallBMWClassic) {
			// if Mini Connected is installed, prompt to install BMW Connected Classic
			// otherwise, prompt to install Mini Connected Classic (most users will be BMW)
			connectionDebugging.isMiniConnectedInstalled
		}

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