package me.hufman.androidautoidrive.carapp

import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingClient
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import me.hufman.idriveconnectionkit.rhmi.RHMIApplication
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationEtch
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationIdempotent
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationSynchronized

class CarConnectionBuilder(val connectionSettings: IDriveConnectionListener, var securityAccess: SecurityAccess, var carAppAssets: CarAppResources, var appId: String) {
	var callbackClient: BMWRemotingClient? = null
	var setTextDb = true
	var setImageDb = true

	fun connect(): BMWRemotingServer {
		val carConnection = IDriveConnection.getEtchConnection(connectionSettings.host ?: "127.0.0.1", connectionSettings.port ?: 8003,
				callbackClient ?: BaseBMWRemotingClient())
		val appCert = carAppAssets.getAppCertificate(IDriveConnectionListener.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = securityAccess.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)
		return carConnection
	}

	fun buildApp(carConnection: BMWRemotingServer): RHMIApplicationSynchronized {
		// create the app in the car
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData(appId, BMWRemoting.VersionInfo(0, 1, 0),
				appId, "me.hufman"))
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppAssets.getUiDescription())
		if (setTextDb) {
			RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.TEXTDB, carAppAssets.getTextsDB(connectionSettings.brand ?: "common"))
		}
		if (setImageDb) {
			RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, carAppAssets.getImagesDB(connectionSettings.brand ?: "common"))
		}
		carConnection.rhmi_initialize(rhmiHandle)

		// load the app resources
		val carApp = RHMIApplicationSynchronized(RHMIApplicationIdempotent(RHMIApplicationEtch(carConnection, rhmiHandle)))
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)

		// set up listeners
		carApp.runSynchronized {
			carConnection.rhmi_addActionEventHandler(rhmiHandle, appId, -1)
			carConnection.rhmi_addHmiEventHandler(rhmiHandle, appId, -1, -1)
		}

		return carApp
	}

	fun buildApp(): RHMIApplication {
		return buildApp(connect())
	}
}