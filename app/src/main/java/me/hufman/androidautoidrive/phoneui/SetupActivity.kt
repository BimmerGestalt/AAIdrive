package me.hufman.androidautoidrive.phoneui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import kotlinx.android.synthetic.main.activity_setup.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BuildConfig
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.connections.BclStatusListener
import me.hufman.androidautoidrive.connections.CarConnectionDebugging
import java.text.SimpleDateFormat
import java.util.*

class SetupActivity : AppCompatActivity() {
	companion object {
		const val REDRAW_DEBOUNCE = 100
		const val INTENT_REDRAW = "me.hufman.androidautoidrive.SetupActivity.REDRAW"
		fun redraw(context: Context) {
			context.sendBroadcast(Intent(INTENT_REDRAW))
		}
	}

	val connectionDebugging by lazy {
		CarConnectionDebugging(this) { redraw() }
	}

	var bclNextRedraw: Long = 0
	val bclStatusListener = BclStatusListener(this) {
		if (bclNextRedraw < SystemClock.uptimeMillis()) {
			redraw()
			bclNextRedraw = SystemClock.uptimeMillis() + REDRAW_DEBOUNCE
		}
	}


	val redrawListener = object: BroadcastReceiver() {
		override fun onReceive(p0: Context?, p1: Intent?) {
			redraw()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_setup)

		btnInstallBMW.setOnClickListener { installConnected("bmw") }
		btnInstallMini.setOnClickListener { installConnected("mini") }
		btnInstallBMWClassic.setOnClickListener { installConnectedClassic("bmw") }
		btnInstallMiniClassic.setOnClickListener { installConnectedClassic("mini") }

		swAdvancedSettings.setOnClickListener {
			AppSettings.saveSetting(this, AppSettings.KEYS.SHOW_ADVANCED_SETTINGS, swAdvancedSettings.isChecked.toString())
			redraw()
		}

		this.registerReceiver(redrawListener, IntentFilter(INTENT_REDRAW))

		connectionDebugging.register()
	}

	override fun onResume() {
		super.onResume()

		redraw()

		bclStatusListener.subscribe()
	}

	override fun onPause() {
		super.onPause()

		bclStatusListener.unsubscribe()
	}

	override fun onDestroy() {
		super.onDestroy()
		this.unregisterReceiver(redrawListener)
		connectionDebugging.unregister()
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
		// only redraw on the main thread
		if (!Looper.getMainLooper().isCurrentThread) {
			sendBroadcast(Intent(INTENT_REDRAW))
			return
		}

		val deviceName = Settings.Global.getString(this.contentResolver, "device_name")

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
		paneBclDisconnected.visible = (connectionDebugging.isBTConnected || connectionDebugging.isUsbAccessoryConnected) &&
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
		val chassisCode = ChassisCode.fromCode(DebugStatus.carCapabilities["vehicle.type"] ?: "Unknown")
		txtCarConnected.text = if (chassisCode != null) {
			resources.getString(R.string.notification_description_chassiscode, chassisCode.toString())
		} else {
			when (connectionDebugging.idriveListener.brand?.toLowerCase()) {
				"bmw" -> resources.getString(R.string.notification_description_bmw)
				"mini" -> resources.getString(R.string.notification_description_mini)
				else -> resources.getString(R.string.notification_description)
			}
		}
		// second half
		val buildTime = SimpleDateFormat.getDateTimeInstance().format(Date(BuildConfig.BUILD_TIME))
		txtBuildInfo.text = getString(R.string.txt_build_info, BuildConfig.VERSION_NAME, buildTime)

		val showAdvancedSettings = AppSettings[AppSettings.KEYS.SHOW_ADVANCED_SETTINGS].toBoolean()
		swAdvancedSettings.isChecked = showAdvancedSettings
		paneAdvancedInfo.visible = showAdvancedSettings

		txtBclReport.text = bclStatusListener.toString()
		paneBclReport.visible = bclStatusListener.state != "UNKNOWN" && bclStatusListener.staleness < 30000

		val carCapabilities = synchronized(DebugStatus.carCapabilities) {
			DebugStatus.carCapabilities.map {
				"${it.key}: ${it.value}"
			}.sorted().joinToString("\n")
		}
		txtCarCapabilities.text = carCapabilities
		paneCarCapabilities.visible = carCapabilities.isNotEmpty()
	}

	val isUSA
		get() = this.resources.configuration.locale.country == "US"

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

}

var View.visible: Boolean
	get() { return this.visibility == VISIBLE }
	set(value) {this.visibility = if (value) VISIBLE else GONE}