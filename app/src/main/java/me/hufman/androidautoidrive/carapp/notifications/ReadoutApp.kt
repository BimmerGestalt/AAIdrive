package me.hufman.androidautoidrive.carapp.notifications

import com.google.gson.Gson
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import io.bimmergestalt.idriveconnectkit.CDS
import io.bimmergestalt.idriveconnectkit.IDriveConnection
import io.bimmergestalt.idriveconnectkit.Utils.rhmi_setResourceCached
import io.bimmergestalt.idriveconnectkit.android.CarAppResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import io.bimmergestalt.idriveconnectkit.rhmi.*
import me.hufman.androidautoidrive.carapp.*

class ReadoutApp(val iDriveConnectionStatus: IDriveConnectionStatus, val securityAccess: SecurityAccess, carAppAssets: CarAppResources) {
	val carConnection: BMWRemotingServer
	val carApp: RHMIApplication
	val infoState: RHMIState.PlainState
	val readoutController: ReadoutController

	init {
		val cdsData = CDSDataProvider()
		val listener = ReadoutAppListener(cdsData)
		carConnection = IDriveConnection.getEtchConnection(iDriveConnectionStatus.host ?: "127.0.0.1", iDriveConnectionStatus.port ?: 8003, listener)
		val readoutCert = carAppAssets.getAppCertificate(iDriveConnectionStatus.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(readoutCert)
		val sas_login = securityAccess.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)

		// create the app in the car
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive.notification.readout", BMWRemoting.VersionInfo(0, 1, 0),
				"me.hufman.androidautoidrive.notification.readout", "me.hufman"))
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppAssets.getUiDescription())
		// no icons or text, so sneaky
		carConnection.rhmi_initialize(rhmiHandle)

		carApp = RHMIApplicationSynchronized(RHMIApplicationIdempotent(RHMIApplicationEtch(carConnection, rhmiHandle)), carConnection)
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)
		this.readoutController = ReadoutController.build(carApp, "NotificationReadout")

		val destStateId = carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().first().getAction()?.asHMIAction()?.target!!
		this.infoState = carApp.states[destStateId] as RHMIState.PlainState

		initWidgets()

		// register for readout updates
		cdsData.setConnection(CDSConnectionEtch(carConnection))
		cdsData.subscriptions[CDS.HMI.TTS] = {
			val state = try {
				Gson().fromJson(it["TTSState"], TTSState::class.java)
			} catch (e: Exception) { null }
			if (state != null) {
				readoutController.onTTSEvent(state)
			}
		}
	}

	class ReadoutAppListener(val cdsEventHandler: CDSEventHandler): BaseBMWRemotingClient() {
		override fun cds_onPropertyChangedEvent(handle: Int?, ident: String?, propertyName: String?, propertyValue: String?) {
			cdsEventHandler.onPropertyChangedEvent(ident, propertyValue)
		}
	}

	fun initWidgets() {
		val list = infoState.componentsList.filterIsInstance<RHMIComponent.List>().first()
		list.setEnabled(false)
		list.setVisible(true)
		val data = RHMIModel.RaListModel.RHMIListConcrete(1)
		data.addRow(arrayOf(L.READOUT_DESCRIPTION))
		list.getModel()?.setValue(data, 0, 1, 1)
	}

	fun disconnect() {
		try {
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch ( e: java.io.IOError) {
		} catch (e: RuntimeException) {}
	}
}