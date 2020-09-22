package me.hufman.androidautoidrive.carapp.assistant

import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.androidautoidrive.GraphicsHelpers
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import kotlin.math.min
import kotlin.math.roundToInt

val AssistantAppInfo.amAppIdentifier: String
	get() = "androidautoidrive.assistant.${this.packageName}"

class AssistantApp(val securityAccess: SecurityAccess, val carAppAssets: CarAppResources, val controller: AssistantController, val graphicsHelpers: GraphicsHelpers) {
	val TAG = "AssistantApp"
	val carConnection = createRHMIApp()
	val amHandle: Int

	private fun createRHMIApp(): BMWRemotingServer {
		val carappListener = CarAppListener()
		val carConnection = IDriveConnection.getEtchConnection(IDriveConnectionListener.host ?: "127.0.0.1", IDriveConnectionListener.port ?: 8003, carappListener)
		val appCert = carAppAssets.getAppCertificate(IDriveConnectionListener.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = securityAccess.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)

		return carConnection
	}

	init {
		amHandle = carConnection.am_create("0", "\u0000\u0000\u0000\u0000\u0000\u0002\u0000\u0000".toByteArray())
	}

	fun onCreate() {
		carConnection.am_addAppEventHandler(amHandle, "me.hufman.androidautoidrive.assistant")
		val assistants = controller.getAssistants()
		assistants.forEach {
			carConnection.am_registerApp(amHandle, it.amAppIdentifier, getAMInfo(it))
		}
	}

	/**
	 * Recreate an AM app entry, which removes the spinning animation
	 */
	fun amRecreateApp(appInfo: AssistantAppInfo) {
		try {
			carConnection.am_registerApp(amHandle, appInfo.amAppIdentifier, getAMInfo(appInfo))
		} catch (e: Exception) {
			Log.w(TAG, "Received exception during AM app redraw", e)
		}
	}

	fun onDestroy() {
	}

	inner class CarAppListener: BaseBMWRemotingClient() {
		override fun am_onAppEvent(handle: Int?, ident: String?, appId: String?, event: BMWRemoting.AMEvent?) {
			val assistants = controller.getAssistants()
			assistants.forEach {
				if (it.amAppIdentifier == appId) {
					controller.triggerAssistant(it)
					Thread.sleep(2000)
					amRecreateApp(it)
				}
			}
		}
	}

	fun getAMInfo(app: AssistantAppInfo): Map<Int, Any> {
		val amInfo = mutableMapOf<Int, Any>(
				0 to 145,   // basecore version
				1 to app.name,  // app name
				2 to graphicsHelpers.compress(app.icon, 48, 48), // icon
				3 to "OnlineServices",   // section
				4 to true,
				5 to 800 - getAppWeight(app),   // weight
				8 to -1  // mainstateId
		)
		// language translations, dunno which one is which
		for (languageCode in 101..123) {
			amInfo[languageCode] = app.name
		}

		return amInfo
	}

	/** What weight to assign for the AM app, to sort it in the list properly */
	fun getAppWeight(app: AssistantAppInfo): Int {
		val name = app.name.toLowerCase().toCharArray().filter { it.isLetter() }
		var score = min(name[0].toInt() - 'a'.toInt(), 'z'.toInt())
		score = score * 6 + ((name[1].toInt() / 6.0).roundToInt())
		return score
	}
}