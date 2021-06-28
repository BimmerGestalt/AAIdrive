package me.hufman.androidautoidrive.carapp.notifications

import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.androidautoidrive.CarAppAssetManager
import me.hufman.androidautoidrive.carapp.*
import me.hufman.androidautoidrive.carapp.notifications.views.ID5PopupView
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionStatus
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import me.hufman.idriveconnectionkit.rhmi.*
import java.lang.RuntimeException
import java.util.zip.ZipInputStream

class ID5StatusbarApp(val iDriveConnectionStatus: IDriveConnectionStatus, val securityAccess: SecurityAccess, carAppAssets: CarAppAssetManager, graphicsHelpers: GraphicsHelpers) {
	val carConnection: BMWRemotingServer
	val carApp: RHMIApplication
	val infoState: RHMIState.PlainState
	val popupView: ID5PopupView

	val focusTriggerController: FocusTriggerController
	var showNotificationController: ShowNotificationController? = null
	val notificationEvent: RHMIEvent.NotificationEvent
	val statusbarController: ID5NotificationCenter

	init {
		val listener = ID5StatusbarListener()
		carConnection = IDriveConnection.getEtchConnection(iDriveConnectionStatus.host ?: "127.0.0.1", iDriveConnectionStatus.port ?: 8003, listener)
		val appCert = carAppAssets.getAppCertificate(iDriveConnectionStatus.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = securityAccess.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)

		// create the app in the car
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive.notification.statusbar", BMWRemoting.VersionInfo(0, 1, 0),
				"me.hufman.androidautoidrive.notification.statusbar", "me.hufman"))
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppAssets.getUiDescription())
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, carAppAssets.getImagesDB(iDriveConnectionStatus.brand ?: "common"))
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.WIDGETDB, carAppAssets.getWidgetsDB(iDriveConnectionStatus.brand ?: "common"))

		// no text, so sneaky
		carConnection.rhmi_initialize(rhmiHandle)

		carApp = RHMIApplicationSynchronized(RHMIApplicationIdempotent(RHMIApplicationEtch(carConnection, rhmiHandle)), carConnection)
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)

		val focusEvent = carApp.events.values.filterIsInstance<RHMIEvent.FocusEvent>().minByOrNull { it.id }!!
		focusTriggerController = FocusTriggerController(focusEvent) {}

		listener.server = carConnection
		listener.app = carApp

		// set up statusbar controller
		notificationEvent = carApp.events.values.filterIsInstance<RHMIEvent.NotificationEvent>().first()
		val imageId = locateEnvelopeImageId(carAppAssets)
		statusbarController = ID5NotificationCenter(notificationEvent, imageId)

		this.infoState = carApp.states.values.filterIsInstance<RHMIState.PlainState>().first {
			it.componentsList.isNotEmpty() &&
			it.componentsList[0] is RHMIComponent.List
		}

		this.popupView = ID5PopupView(carApp.states.values.filterIsInstance<RHMIState.PopupState>().first(), graphicsHelpers, imageId)

		// register for events from the car
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "me.hufman.androidautoidrive.notifications", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "me.hufman.androidautoidrive.notifications", -1, -1)

		initWidgets()
	}

	class ID5StatusbarListener: BaseBMWRemotingClient() {
		var server: BMWRemotingServer? = null
		var app: RHMIApplication? = null

		override fun rhmi_onActionEvent(handle: Int?, ident: String?, actionId: Int?, args: MutableMap<*, *>?) {
			Log.w(TAG, "Received rhmi_onActionEvent: handle=$handle ident=$ident actionId=$actionId")
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
				Log.e(TAG, "Exception while calling onActionEvent handler! $e")
				synchronized(server!!) {
					server?.rhmi_ackActionEvent(handle, actionId, 1, true)
				}
			}
		}

		override fun rhmi_onHmiEvent(handle: Int?, ident: String?, componentId: Int?, eventId: Int?, args: MutableMap<*, *>?) {
			val msg = "Received rhmi_onHmiEvent: handle=$handle ident=$ident componentId=$componentId eventId=$eventId args=${args?.toString()}"
			Log.w(TAG, msg)

			val state = app?.states?.get(componentId)
			state?.onHmiEvent(eventId, args)

			val component = app?.components?.get(componentId)
			component?.onHmiEvent(eventId, args)
		}
	}

	fun initWidgets() {
		carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach { button ->
			button.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionCallback {
				focusTriggerController.focusState(infoState, true)
			}
		}

		infoState.componentsList.forEach {
			it.setVisible(false)
		}

		val list = infoState.componentsList.filterIsInstance<RHMIComponent.List>().first()
		list.setEnabled(false)
		list.setVisible(true)
		val data = RHMIModel.RaListModel.RHMIListConcrete(1)
		data.addRow(arrayOf(L.NOTIFICATION_CENTER_APP + "\n"))
		list.getModel()?.setValue(data, 0, 1, 1)

		notificationEvent.getActionId()?.asRAAction()?.rhmiActionCallback = statusbarController
		popupView.onClicked = {
			showNotificationController?.showFromFocusEvent(it, true)
		}
		statusbarController.onClicked = {
			showNotificationController?.showFromFocusEvent(it, true)
		}

		popupView.initWidgets()
	}

	private fun locateEnvelopeImageId(resources: CarAppResources): Int {
		val file = resources.getImagesDB("common") ?: return 0
		val zipfile = ZipInputStream(file)

		var fallbackId = 0
		while (true) {
			val next = zipfile.nextEntry ?: break
			if (fallbackId == 0) {
				fallbackId = next.name.split('.')[0].toInt()
			}
			zipfile.readBytes()     // load up the crc
			println("Found ${next.name} with ${next.crc} and size ${next.size}")
			if (next.crc == 0x901E29E5) {
				return next.name.split('.')[0].toInt()
			}
		}
		return fallbackId
	}

	fun disconnect() {
		try {
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch ( e: java.io.IOError) {
		} catch (e: RuntimeException) {}
	}
}