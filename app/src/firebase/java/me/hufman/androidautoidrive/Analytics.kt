package me.hufman.androidautoidrive

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import me.hufman.androidautoidrive.music.MusicAppInfo

object Analytics: AnalyticsProvider {
	private lateinit var firebaseAnalytics: FirebaseAnalytics

	override fun init(context: Context) {
		firebaseAnalytics = FirebaseAnalytics.getInstance(context)
	}

	override fun reportMusicAppProbe(appInfo: MusicAppInfo) {
		val bundle = Bundle().apply {
			putString("appId", appInfo.packageName)
			putString("name", appInfo.name)
			putString("connectable", if (appInfo.connectable) "true" else "false")
			putString("browseable", if (appInfo.browseable) "true" else "false")
			putString("searchable", if (appInfo.searchable) "true" else "false")
		}
		firebaseAnalytics.logEvent("MusicAppProbe", bundle)
	}

	override fun reportCarProbeFailure(port: Int, message: String?) {
		val bundle = Bundle().apply {
			putLong("port", port.toLong())
			putString("message", message)
		}
		firebaseAnalytics.logEvent("CarProberFailure", bundle)
	}

	override fun reportCarProbeDiscovered(port: Int?, vehicleType: String?, hmiType: String?) {
		val bundle = Bundle().apply {
			if (port != null)
				putLong("port", port.toLong())
			putString("vehicle_type", vehicleType)
			putString("hmi_type", hmiType)
		}
		firebaseAnalytics.logEvent("CarProberDiscovered", bundle)
	}

	override fun reportCarCapabilities(capabilities: Map<String, String?>) {
		val bundle = Bundle().apply {
			capabilities.keys.forEach { key ->
				val value = capabilities[key]
				if (value != null) {
					val keyName = key.replace(Regex("[^a-zA-Z0-9_]"), "_")
					if (value.toLongOrNull() != null) {
						putLong(keyName, value.toLong())
					} else {
						putString(keyName, value)
					}
				}
			}
		}
		firebaseAnalytics.logEvent("CarCapabilities", bundle)
	}
}