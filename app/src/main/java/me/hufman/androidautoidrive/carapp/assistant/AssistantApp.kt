package me.hufman.androidautoidrive.carapp.assistant

import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import me.hufman.androidautoidrive.carapp.AMAppList
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionStatus
import me.hufman.idriveconnectionkit.android.security.SecurityAccess

class AssistantApp(val iDriveConnectionStatus: IDriveConnectionStatus, val securityAccess: SecurityAccess, val carAppAssets: CarAppResources, val controller: AssistantController, val graphicsHelpers: GraphicsHelpers) {
	val TAG = "AssistantApp"
	val carConnection = createRHMIApp()
	val amAppList = AMAppList<AssistantAppInfo>(carConnection, graphicsHelpers, "me.hufman.androidautoidrive.assistant")

	private fun createRHMIApp(): BMWRemotingServer {
		val carappListener = CarAppListener()
		val carConnection = IDriveConnection.getEtchConnection(iDriveConnectionStatus.host ?: "127.0.0.1", iDriveConnectionStatus.port ?: 8003, carappListener)
		val appCert = carAppAssets.getAppCertificate(iDriveConnectionStatus.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = securityAccess.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)

		return carConnection
	}

	fun onCreate() {
		val assistants = controller.getAssistants()
		amAppList.setApps(assistants.toList())
	}

	fun onDestroy() {
	}

	inner class CarAppListener: BaseBMWRemotingClient() {
		override fun am_onAppEvent(handle: Int?, ident: String?, appId: String?, event: BMWRemoting.AMEvent?) {
			appId ?: return
			val assistant = amAppList.getAppInfo(appId) ?: return
			controller.triggerAssistant(assistant)
			Thread.sleep(2000)
			amAppList.redrawApp(assistant)
		}
	}
}