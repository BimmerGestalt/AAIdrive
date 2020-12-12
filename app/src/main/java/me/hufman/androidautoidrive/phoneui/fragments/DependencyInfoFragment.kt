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
import kotlinx.android.synthetic.main.fragment_dependencyinfo.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.connections.CarConnectionDebugging
import me.hufman.androidautoidrive.phoneui.showEither
import me.hufman.androidautoidrive.phoneui.visible

class DependencyInfoFragment: Fragment() {
	val connectionDebugging by lazy {
		CarConnectionDebugging(requireContext()) {
			activity?.runOnUiThread { redraw() }
		}
	}
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_dependencyinfo, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		btnInstallBMW.setOnClickListener { installConnected("bmw") }
		btnInstallMini.setOnClickListener { installConnected("mini") }
		btnInstallBMWClassic.setOnClickListener { installConnectedClassic("bmw") }
		btnInstallMiniClassic.setOnClickListener { installConnectedClassic("mini") }
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

	fun redraw() {
		if (!isResumed) return

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
	}
}