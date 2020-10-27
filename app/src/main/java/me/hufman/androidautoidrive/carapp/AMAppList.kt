package me.hufman.androidautoidrive.carapp

import android.graphics.drawable.Drawable
import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import me.hufman.androidautoidrive.GraphicsHelpers
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.min
import kotlin.math.roundToInt

enum class AMCategory(val value: String) {
	ADDRESSBOOK("Addressbook"),
	MULTIMEDIA("Multimedia"),
	NAVIGATION("Navigation"),
	ONLINE_SERVICES("OnlineServices"),
	PHONE("Phone"),
	RADIO("Radio"),
	SETTINGS("Settings"),
	VEHICLE_INFORMATION("VehicleInformation"),
}

interface AMAppInfo {
	val packageName: String
	val name: String
	val icon: Drawable
	val category: AMCategory

	val amAppIdentifier
		get() = "androidautoidrive.$packageName"
}

class AMAppList<T: AMAppInfo>(val connection: BMWRemotingServer, val graphicsHelpers: GraphicsHelpers, val amIdent: String, val adjustment: Int = 0) {
	companion object {
		val TAG = "AMAppList"

		fun getAppWeight(appName: String): Int {
			val name = appName.toLowerCase().toCharArray().filter { it.isLetter() }
			var score = min(name[0].toInt() - 'a'.toInt(), 'z'.toInt())
			score = score * 6 + ((name[1].toInt() / 6.0).roundToInt())
			return score
		}
	}
	private var amHandle: Int = createAm()
	private val knownApps = HashMap<String, T>()        // keyed by amAppIdentifier

	fun getAMInfo(app: AMAppInfo): Map<Int, Any> {
		val amInfo = mutableMapOf<Int, Any>(
				0 to 145,   // basecore version
				1 to app.name,  // app name
				2 to graphicsHelpers.compress(app.icon, 48, 48), // icon
				3 to app.category.value,   // section
				4 to true,
				5 to 800 - (getAppWeight(app.name) - adjustment),   // weight
				8 to -1  // mainstateId
		)
		// language translations, dunno which one is which
		for (languageCode in 101..123) {
			amInfo[languageCode] = app.name
		}

		return amInfo
	}

	/**
	 * When handling an am_onAppEvent, this function
	 * converts the given appId to the original appInfo object
	 */
	fun getAppInfo(appId: String): T? {
		return knownApps[appId]
	}

	/**
	 * Updates the list of displayed apps
	 * Make sure to synchronize it around the Etch connection
	 */
	fun setApps(apps: List<T>) {
		// then create all the apps
		for (app in apps) {
			if (!knownApps.containsKey(app.amAppIdentifier)) {
				createApp(app)
				knownApps[app.amAppIdentifier] = app
			}
		}
	}

	fun redrawApp(app: T) {
		if (knownApps.containsKey(app.amAppIdentifier)) {
			createApp(app)
		}
	}

	private fun createAm(): Int {
		return synchronized(connection) {
			val handle = connection.am_create("0", "\u0000\u0000\u0000\u0000\u0000\u0002\u0000\u0000".toByteArray())
			try {
				connection.am_addAppEventHandler(handle, amIdent)
			} catch (e: BMWRemoting.ServiceException) {}
			handle
		}
	}

	private fun createApp(app: T) {
		Log.d(TAG, "Creating am app for app ${app.name}")
		connection.am_registerApp(amHandle, app.amAppIdentifier, getAMInfo(app))
	}
}