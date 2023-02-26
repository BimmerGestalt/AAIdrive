package me.hufman.androidautoidrive.carapp.notifications

import android.util.Log
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
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.carapp.*
import me.hufman.androidautoidrive.carapp.carinfo.CarDetailedInfo
import me.hufman.androidautoidrive.carapp.carinfo.views.CarDetailedView
import me.hufman.androidautoidrive.cds.*

class ReadoutApp(val iDriveConnectionStatus: IDriveConnectionStatus, val securityAccess: SecurityAccess, carAppAssets: CarAppResources) {
	val carConnection: BMWRemotingServer
	val carApp: RHMIApplication
	val infoState: CarDetailedView
	val readoutController: ReadoutController

	init {
		val cdsData = CDSDataProvider()
		val listener = ReadoutAppListener(cdsData)
		carConnection = IDriveConnection.getEtchConnection(iDriveConnectionStatus.host ?: "127.0.0.1", iDriveConnectionStatus.port ?: 8003, listener)
		val readoutCert = carAppAssets.getAppCertificate(iDriveConnectionStatus.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(readoutCert)
		val sas_login = securityAccess.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)
		listener.server = carConnection

		// create the app in the car
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive.notification.readout", BMWRemoting.VersionInfo(0, 1, 0),
				"me.hufman.androidautoidrive.notification.readout", "me.hufman"))
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppAssets.getUiDescription())
		// no icons or text, so sneaky
		carConnection.rhmi_initialize(rhmiHandle)
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "me.hufman.androidautoidrive.notification.readout", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "me.hufman.androidautoidrive.notification.readout", -1, -1)

		carApp = RHMIApplicationSynchronized(RHMIApplicationIdempotent(RHMIApplicationEtch(carConnection, rhmiHandle)), carConnection)
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)
		this.readoutController = ReadoutController.build(carApp, "NotificationReadout")
		listener.app = carApp

		val destStateId = carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().first().getAction()?.asHMIAction()?.target!!
		this.infoState = CarDetailedView(carApp.states[destStateId] as RHMIState, CarDetailedInfo(CDSMetrics(CarInformation())))

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
		var server: BMWRemotingServer? = null
		var app: RHMIApplication? = null
		override fun cds_onPropertyChangedEvent(handle: Int?, ident: String?, propertyName: String?, propertyValue: String?) {
			cdsEventHandler.onPropertyChangedEvent(ident, propertyValue)
		}

		override fun rhmi_onActionEvent(handle: Int?, ident: String?, actionId: Int?, args: MutableMap<*, *>?) {
			try {
				app?.actions?.get(actionId)?.asRAAction()?.rhmiActionCallback?.onActionEvent(args)
				synchronized(server!!) {
					server?.rhmi_ackActionEvent(handle, actionId, 1, true)
				}
			} catch (e: RHMIActionAbort) {
				// Action handler requested that we don't claim success
				synchronized(server!!) {
					server?.rhmi_ackActionEvent(handle, actionId, 1, false)
				}
			} catch (e: Exception) {
				Log.e(TAG, "Exception while calling onActionEvent handler!", e)
				synchronized(server!!) {
					server?.rhmi_ackActionEvent(handle, actionId, 1, true)
				}
			}
		}

		override fun rhmi_onHmiEvent(handle: Int?, ident: String?, componentId: Int?, eventId: Int?, args: MutableMap<*, *>?) {
			try {
				// generic event handler
				app?.states?.get(componentId)?.onHmiEvent(eventId, args)
				app?.components?.get(componentId)?.onHmiEvent(eventId, args)
			} catch (e: Exception) {
				Log.e(TAG, "Received exception while handling rhmi_onHmiEvent", e)
			}
		}
	}

	fun initWidgets() {
		infoState.initWidgets()
	}

	fun disconnect() {
		try {
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch ( e: java.io.IOError) {
		} catch (e: RuntimeException) {}
	}
}