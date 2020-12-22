package me.hufman.androidautoidrive.phoneui.controllers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

class DependencyInfoController(val appContext: Context) {
	val isUSA
		get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			appContext.resources.configuration.locales.get(0).country == "US"
		} else {
			@Suppress("DEPRECATION")
			appContext.resources.configuration.locale.country == "US"
		}

	fun installConnected(brand: String = "bmw") {
		val packageName = if (isUSA) "de.$brand.connected.na" else "de.$brand.connected"
		val intent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
			flags = Intent.FLAG_ACTIVITY_NEW_TASK
		}
		appContext.startActivity(intent)
	}

	fun installConnectedClassic(brand: String = "bmw") {
		val packageName = if (isUSA) "com.bmwgroup.connected.$brand.usa" else "com.bmwgroup.connected.$brand"
		val intent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
			flags = Intent.FLAG_ACTIVITY_NEW_TASK
		}
		appContext.startActivity(intent)
	}

	fun installBMWConnected() = installConnected("bmw")
	fun installMiniConnected() = installConnected("mini")
	fun installBMWConnectedClassic() = installConnectedClassic("bmw")
	fun installMiniConnectedClassic() = installConnectedClassic("mini")
}