package me.hufman.androidautoidrive.carapp.carinfo

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Handler
import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import io.bimmergestalt.idriveconnectkit.IDriveConnection
import io.bimmergestalt.idriveconnectkit.RHMIUtils.rhmi_setResourceCached
import io.bimmergestalt.idriveconnectkit.android.CarAppResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIApplication
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIApplicationEtch
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIApplicationIdempotent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIApplicationSynchronized
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIComponent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIEvent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import io.bimmergestalt.idriveconnectkit.rhmi.deserialization.loadFromXML
import kotlinx.coroutines.android.asCoroutineDispatcher
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BuildConfig
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.AMCategory
import me.hufman.androidautoidrive.carapp.FocusTriggerController
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.RHMIApplicationSwappable
import me.hufman.androidautoidrive.carapp.ReadoutCommands
import me.hufman.androidautoidrive.carapp.ReadoutCommandsRHMI
import me.hufman.androidautoidrive.carapp.ReadoutController
import me.hufman.androidautoidrive.carapp.TTSState
import me.hufman.androidautoidrive.carapp.carinfo.views.CarDetailedView
import me.hufman.androidautoidrive.carapp.carinfo.views.CategoryView
import me.hufman.androidautoidrive.carapp.notifications.TAG
import me.hufman.androidautoidrive.cds.CDSConnectionEtch
import me.hufman.androidautoidrive.cds.CDSDataProvider
import me.hufman.androidautoidrive.cds.CDSEventHandler
import me.hufman.androidautoidrive.cds.CDSMetrics
import me.hufman.androidautoidrive.cds.flow
import me.hufman.androidautoidrive.cds.onPropertyChangedEvent
import me.hufman.androidautoidrive.cds.subscriptions
import me.hufman.androidautoidrive.utils.Utils

class CarInfoApp(val iDriveConnectionStatus: IDriveConnectionStatus, val securityAccess: SecurityAccess,
                 val carAppAssets: CarAppResources, val unsignedCarAppAssets: CarAppResources,
                 val handler: Handler, val resources: Resources, val appSettings: AppSettings) {
	private val coroutineContext = handler.asCoroutineDispatcher()
	val carConnection: BMWRemotingServer
	var rhmiHandle: Int = -1
	val carAppSwappable: RHMIApplicationSwappable
	val carApp: RHMIApplication
	val amHandle: Int
	val focusTriggerController: FocusTriggerController
	val infoState: CarDetailedView
	val categoryState: CategoryView
	val readoutCommands: ReadoutCommands

	init {
		val cdsData = CDSDataProvider()
		val listener = CarInfoAppListener(cdsData)
		carConnection = IDriveConnection.getEtchConnection(iDriveConnectionStatus.host ?: "127.0.0.1", iDriveConnectionStatus.port ?: 8003, listener)
		val readoutCert = carAppAssets.getAppCertificate(iDriveConnectionStatus.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(readoutCert)
		val sas_login = securityAccess.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)
		listener.server = carConnection

		// lock during initialization
		synchronized(carConnection) {
			carAppSwappable = RHMIApplicationSwappable(createRhmiApp())
			carApp = RHMIApplicationSynchronized(carAppSwappable, carConnection)
			listener.app = carApp
			carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)
			val focusEvent = carApp.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first()
			focusTriggerController = FocusTriggerController(focusEvent) {
				recreateRhmiApp()
			}

			this.readoutCommands = ReadoutCommandsRHMI.build(carApp)

			val carInfo = CarInformation().also {
				cdsData.flow.defaultIntervalLimit = 100
			}
			val carDetailedInfo = CarDetailedInfo(carInfo.capabilities, CDSMetrics(carInfo))
			val destStateId = carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().first().getAction()?.asHMIAction()?.target!!
			this.infoState = CarDetailedView(carApp.states[destStateId] as RHMIState, coroutineContext, carDetailedInfo)
			val categoryState = infoState.state.componentsList.filterIsInstance<RHMIComponent.Button>().first().getAction()?.asHMIAction()?.getTargetState()!!
			this.categoryState = CategoryView(categoryState, carDetailedInfo, appSettings)

			initWidgets()
		}

		// register for car info updates
		cdsData.setConnection(CDSConnectionEtch(carConnection))

		// set up the AM icon in the "Addressbook"/Communications section
		amHandle = carConnection.am_create("0", "\u0000\u0000\u0000\u0000\u0000\u0002\u0000\u0000".toByteArray())
		carConnection.am_addAppEventHandler(amHandle, "me.hufman.androidautoidrive.notification.readout")   // the old name, which might be in people's bookmarks
		createAmApp()
	}

	/** creates the app in the car */
	fun createRhmiApp(): RHMIApplication {
		// create the app in the car
		rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive.notification.readout", BMWRemoting.VersionInfo(0, 1, 0),
			"me.hufman.androidautoidrive.notification.readout", "me.hufman"))
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppAssets.getUiDescription())
		if (BuildConfig.SEND_UNSIGNED_RESOURCES) {
			try {
				carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.TEXTDB, unsignedCarAppAssets.getTextsDB(iDriveConnectionStatus.brand ?: "common"))
				carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, unsignedCarAppAssets.getImagesDB(iDriveConnectionStatus.brand ?: "common"))
			} catch (e: Exception) {
				Log.w(TAG, "Unsigned resources were not accepted by car")
			}
		} else {
			// no icons or text, so sneaky
		}

		carConnection.rhmi_initialize(rhmiHandle)
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "me.hufman.androidautoidrive.notification.readout", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "me.hufman.androidautoidrive.notification.readout", -1, -1)

		return RHMIApplicationIdempotent(RHMIApplicationEtch(carConnection, rhmiHandle))
	}

	/** Recreates the RHMI app in the car */
	fun recreateRhmiApp() {
		synchronized(carConnection) {
			// pause events to the underlying connection
			carAppSwappable.isConnected = false
			// destroy the previous RHMI app
			carConnection.rhmi_dispose(rhmiHandle)
			// create a new one
			carAppSwappable.app = createRhmiApp()
			// clear FocusTriggerController because of the new rhmi app
			focusTriggerController.hasFocusedState = false
			// reconnect, triggering a sync down to the new RHMI Etch app
			carAppSwappable.isConnected = true
		}
	}

	@SuppressLint("ResourceType")
	fun createAmApp() {
		val name = L.CARINFO_TITLE
		val carAppImage = try {
			if (iDriveConnectionStatus.brand == "mini") {
				Utils.convertPngToGrayscale(resources.openRawResource(R.drawable.ic_carinfo_mini).readBytes())
			} else {
				Utils.convertPngToGrayscale(resources.openRawResource(R.drawable.ic_carinfo_common).readBytes())
			}
		} catch (e: Resources.NotFoundException) { "" }

		val amInfo = mutableMapOf<Int, Any>(
			0 to 145,   // basecore version
			1 to name,  // app name
			2 to carAppImage,
			3 to AMCategory.VEHICLE_INFORMATION.value,   // section
			4 to true,
			5 to 800,   // weight
			8 to infoState.state.id  // mainstateId
		)
		// language translations, dunno which one is which
		for (languageCode in 101..123) {
			amInfo[languageCode] = name
		}

		synchronized(carConnection) {
			carConnection.am_registerApp(amHandle, "androidautoidrive.notification.readout", amInfo)
		}
	}

	inner class CarInfoAppListener(val cdsEventHandler: CDSEventHandler): BaseBMWRemotingClient() {
		var server: BMWRemotingServer? = null
		var app: RHMIApplication? = null

		fun synced() {
			synchronized(server!!) {
				// the RHMI was definitely initialized, we can continue
			}
		}

		override fun cds_onPropertyChangedEvent(handle: Int?, ident: String?, propertyName: String?, propertyValue: String?) {
			cdsEventHandler.onPropertyChangedEvent(ident, propertyValue)
		}

		override fun am_onAppEvent(handle: Int?, ident: String?, appId: String?, event: BMWRemoting.AMEvent?) {
			synced()
			focusTriggerController.focusState(infoState.state, true)
			createAmApp()
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
		categoryState.initWidgets()
	}

	fun disconnect() {
		try {
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch ( e: java.io.IOError) {
		} catch (e: RuntimeException) {}
	}
}