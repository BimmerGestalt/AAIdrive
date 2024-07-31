package me.hufman.androidautoidrive

import android.content.Context
import android.util.Log
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory
import io.sentry.event.Event
import io.sentry.event.EventBuilder
import io.sentry.event.interfaces.ExceptionInterface
import me.hufman.androidautoidrive.music.MusicAppInfo

object Analytics: AnalyticsProvider {
	override fun init(context: Context) {
		Sentry.init(AndroidSentryClientFactory(context))
	}

	override fun reportMusicAppProbe(appInfo: MusicAppInfo) {
		val event = EventBuilder()
				.withMessage("Probed music app")
				.withTag("appId", appInfo.packageName)
				.withTag("appName", appInfo.name)
				.withTag("controllable", if (appInfo.controllable) "true" else "false")
				.withTag("connectable", if (appInfo.connectable) "true" else "false")
				.withTag("browseable", if (appInfo.browseable) "true" else "false")
				.withTag("searchable", if (appInfo.searchable) "true" else "false")
				.withTag("playsearchable", if (appInfo.playsearchable) "true" else "false")
				.withLevel(Event.Level.DEBUG)
		try {
			Sentry.capture(event)
		} catch (e: Exception) {
			Log.i("Sentry", "Exception while reporting music app probe", e)
		}
	}

	override fun reportCarProbeFailure(port: Int, message: String?, throwable: Throwable?) {
		val event = EventBuilder()
				.withMessage("Failed to probe car: $message")
				.withTag("port", port.toString())
				.withLevel(Event.Level.WARNING)
		try {
			if (throwable != null) {
				val crash = event
						.withSentryInterface(ExceptionInterface(throwable))
				Sentry.capture(crash)
			} else {
				Sentry.capture(event)
			}
		} catch (e: Exception) {
			Log.i("Sentry", "Exception while reporting car probe failure", e)
		}
	}

	override fun reportCarProbeDiscovered(port: Int?, vehicleType: String?, hmiType: String?) {
		val event = EventBuilder()
				.withMessage("Successfully probed to connect to car")
				.withTag("vehicleType", vehicleType ?: "")
				.withTag("hmiType", hmiType ?: "")
				.withTag("port", port.toString())
				.withLevel(Event.Level.DEBUG)
		try {
			Sentry.capture(event)
		} catch (e: Exception) {
			Log.i("Sentry", "Exception while reporting car probe", e)
		}
	}

	override fun reportCarCapabilities(capabilities: Map<String, String?>) {
		var event = EventBuilder()
				.withMessage("Car capabilities")
				.withLevel(Event.Level.DEBUG)
		capabilities.keys.forEach { key ->
			val value = capabilities[key]
			if (value != null) {
				val keyName = key.replace(Regex("[^a-zA-Z0-9_]"), "_")
				event.withTag(keyName, value.toString())
			}
		}
		try {
			Sentry.capture(event)
		} catch (e: Exception) {
			Log.i("Sentry", "Exception while reporting car capabilities", e)
		}
	}

}
