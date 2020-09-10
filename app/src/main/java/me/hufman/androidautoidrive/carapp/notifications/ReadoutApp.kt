package me.hufman.androidautoidrive.carapp.notifications

import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.androidautoidrive.carapp.RHMIUtils
import me.hufman.androidautoidrive.carapp.ReadoutController
import me.hufman.androidautoidrive.loadJSON
import me.hufman.androidautoidrive.toMap
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import me.hufman.idriveconnectionkit.rhmi.*
import java.lang.RuntimeException

class ReadoutApp(val securityAccess: SecurityAccess, val carAppAssets: CarAppResources) {
	val carConnection: BMWRemotingServer
	val carApp: RHMIApplication
	val infoState: RHMIState.PlainState
	val readoutController: ReadoutController

	init {
		val listener = ReadoutAppListener()
		carConnection = IDriveConnection.getEtchConnection(IDriveConnectionListener.host ?: "127.0.0.1", IDriveConnectionListener.port ?: 8003, listener)
		val readoutCert = carAppAssets.getAppCertificate(IDriveConnectionListener.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(readoutCert)
		val sas_login = securityAccess.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)

		// create the app in the car
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive.notification.readout", BMWRemoting.VersionInfo(0, 1, 0),
				"me.hufman.androidautoidrive.notification.readout", "me.hufman"))
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppAssets.getUiDescription())
		// no icons or text, so sneaky
		carConnection.rhmi_initialize(rhmiHandle)

		carApp = RHMIApplicationSynchronized(RHMIApplicationIdempotent(RHMIApplicationEtch(carConnection, rhmiHandle)))
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)
		val readoutController = ReadoutController.build(carApp, "NotificationReadout")
		listener.readoutController = readoutController
		this.readoutController = readoutController

		val destStateId = carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().first().getAction()?.asHMIAction()?.target!!
		this.infoState = carApp.states[destStateId] as RHMIState.PlainState

		initWidgets()

		// register for readout updates
		val cdsHandle = carConnection.cds_create()
		carConnection.cds_addPropertyChangedEventHandler(cdsHandle, "hmi.tts", "113", 200)
	}

	class ReadoutAppListener: BaseBMWRemotingClient() {
		var readoutController: ReadoutController? = null
		override fun cds_onPropertyChangedEvent(handle: Int?, ident: String?, propertyName: String?, propertyValue: String?) {
			val propertyData = loadJSON(propertyValue) ?: return
			val ttsState = propertyData.getJSONObject("TTSState")
			readoutController?.onTTSEvent(ttsState.toMap())
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

	fun onDestroy() {
		try {
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch ( e: java.io.IOError) {
		} catch (e: RuntimeException) {}
	}
}