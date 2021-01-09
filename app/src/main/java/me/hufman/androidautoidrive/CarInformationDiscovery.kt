package me.hufman.androidautoidrive

import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.androidautoidrive.carapp.*
import me.hufman.idriveconnectionkit.CDS
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionStatus
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import java.lang.Exception

class CarInformationDiscovery(iDriveConnectionStatus: IDriveConnectionStatus, securityAccess: SecurityAccess, carAppAssets: CarAppResources, val listener: CarInformationDiscoveryListener?) {

	val carappListener: CarAppListener
	val carConnection: BMWRemotingServer
	val cdsData: CDSData
	var capabilities: Map<String, String?>? = null

	init {
		val cdsData = CDSDataProvider()
		this.cdsData = cdsData
		carappListener = CarAppListener(cdsData)
		carConnection = IDriveConnection.getEtchConnection(iDriveConnectionStatus.host
				?: "127.0.0.1", iDriveConnectionStatus.port ?: 8003, carappListener)
		val appCert = carAppAssets.getAppCertificate(iDriveConnectionStatus.brand
				?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = securityAccess.signChallenge(challenge = sas_challenge)
		carConnection.sas_login(sas_login)
		cdsData.setConnection(CDSConnectionEtch(carConnection))
	}

	fun onCreate() {
		getCapabilities()

		subscribeToCds()
	}

	private fun getCapabilities() {
		val capabilities = carConnection.rhmi_getCapabilities("", 255)

		// report the capabilities to any debug view
		val stringCapabilities = capabilities
				.mapKeys { it.key as String }
				.mapValues { it.value?.toString() }
		this.capabilities = stringCapabilities
		try {
			listener?.onCapabilities(stringCapabilities)
		} catch (e: Exception) {
		}

		// report the capabilities to analytics
		val reportedKeys = setOf(
				"vehicle.type", "vehicle.country", "vehicle.productiondate",
				"hmi.type", "hmi.version", "hmi.display-width", "hmi.display-height", "hmi.role",
				"navi", "map", "tts", "inbox", "speech2text", "voice", "a4axl", "pia", "speedlock",
				"alignment-right", "touch_command"
		)
		val reportedCapabilities = capabilities.filterKeys { it is String && it in reportedKeys }
				.mapKeys { it.key as String }
				.mapValues { it.value?.toString() }
		Analytics.reportCarCapabilities(reportedCapabilities)
	}

	fun subscribeToCds() {
		if (listener != null) {
			// forward all events through
			cdsData.addEventHandler(CDS.NAVIGATION.GUIDANCESTATUS, 1000, listener)
		}
	}

	inner class CarAppListener(val cdsEventHandler: CDSEventHandler): BaseBMWRemotingClient() {
		override fun cds_onPropertyChangedEvent(handle: Int?, ident: String?, propertyName: String?, propertyValue: String?) {
			cdsEventHandler.onPropertyChangedEvent(ident, propertyValue)
		}
	}

	fun onDestroy() {
		try {
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch (e: Exception) {}
	}
}

interface CarInformationDiscoveryListener: CDSEventHandler {
	fun onCapabilities(capabilities: Map<String, String?>)
}