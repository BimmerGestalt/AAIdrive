package me.hufman.androidautoidrive.carapp.calendar

import android.util.Log
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
import me.hufman.androidautoidrive.calendar.CalendarProvider
import me.hufman.androidautoidrive.carapp.*
import me.hufman.androidautoidrive.carapp.calendar.views.CalendarDayView
import me.hufman.androidautoidrive.carapp.calendar.views.CalendarMonthView
import me.hufman.androidautoidrive.carapp.calendar.views.CalendarEventView
import me.hufman.androidautoidrive.carapp.calendar.views.PermissionView
import me.hufman.androidautoidrive.maps.LatLong
import me.hufman.androidautoidrive.carapp.navigation.AddressSearcher
import me.hufman.androidautoidrive.carapp.navigation.NavigationTrigger

class CalendarApp(iDriveConnectionStatus: IDriveConnectionStatus, securityAccess: SecurityAccess, carAppResources: CarAppResources,
                  val calendarProvider: CalendarProvider, val addressSearcher: AddressSearcher, val navigationTrigger: NavigationTrigger) {
	companion object {
		const val TAG = "CalendarApp"
	}

	val carConnection: BMWRemotingServer
	val carApp: RHMIApplication
	val upcomingDestination: UpcomingDestination
	val viewPermission: PermissionView      // show a message if permissions are missing
	val viewMonth: CalendarMonthView
	val viewDay: CalendarDayView
	val viewEvent: CalendarEventView

	init {
		val cdsData = CDSDataProvider()
		val listener = CalendarAppCarListener(cdsData)
		carConnection = IDriveConnection.getEtchConnection(iDriveConnectionStatus.host ?: "127.0.0.1", iDriveConnectionStatus.port ?: 8003, listener)
		val appCert = carAppResources.getAppCertificate(iDriveConnectionStatus.brand ?: "")!!.readBytes()
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = securityAccess.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)

		carApp = createRhmiApp(iDriveConnectionStatus, carAppResources)

		listener.server = carConnection
		listener.app = carApp

		val focusEvent = carApp.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first()
		val focusTriggerController = FocusTriggerController(focusEvent) {}

		viewPermission = PermissionView(carApp.states.values.first { PermissionView.fits(it) })
		viewMonth = CalendarMonthView(carApp.states.values.first { CalendarMonthView.fits(it) }, focusTriggerController, calendarProvider)
		viewDay = CalendarDayView(carApp.states.values.first { CalendarDayView.fits(it) }, calendarProvider)
		viewEvent = CalendarEventView(carApp.states.values.first { CalendarEventView.fits(it) }, addressSearcher, navigationTrigger)
		upcomingDestination = UpcomingDestination(calendarProvider, addressSearcher, navigationTrigger)
		initWidgets()

		// update UpcomingDestination with the current guidance status
		initializeCdsListener(carConnection, cdsData)
	}

	fun createRhmiApp(iDriveConnectionStatus: IDriveConnectionStatus, carAppAssets: CarAppResources): RHMIApplication {
		// create the app in the car
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive.calendar", BMWRemoting.VersionInfo(0, 1, 0),
				"me.hufman.androidautoidrive.calendar", "me.hufman"))
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppAssets.getUiDescription())
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, carAppAssets.getImagesDB(iDriveConnectionStatus.brand ?: "common"))
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.TEXTDB, carAppAssets.getTextsDB(iDriveConnectionStatus.brand ?: "common"))
		carConnection.rhmi_initialize(rhmiHandle)

		// register for events from the car
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "me.hufman.androidautoidrive.calendar", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "me.hufman.androidautoidrive.calendar", -1, -1)

		val carApp = RHMIApplicationSynchronized(RHMIApplicationIdempotent(RHMIApplicationEtch(carConnection, rhmiHandle)), carConnection)
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)
		return carApp
	}

	fun initializeCdsListener(carConnection: BMWRemotingServer, cdsData: CDSDataProvider): CDSDataProvider {
		cdsData.setConnection(CDSConnectionEtch(carConnection))
		cdsData.subscriptions.defaultIntervalLimit = 2200
		cdsData.subscriptions[CDS.NAVIGATION.GUIDANCESTATUS] = {
			upcomingDestination.currentlyNavigating = it["guidanceStatus"]?.asInt == 1
		}
		return cdsData
	}

	class CalendarAppCarListener(val cdsEventHandler: CDSEventHandler): BaseBMWRemotingClient() {
		var server: BMWRemotingServer? = null
		var app: RHMIApplication? = null

		override fun rhmi_onActionEvent(handle: Int?, ident: String?, actionId: Int?, args: MutableMap<*, *>?) {
			Log.w(TAG, "Received rhmi_onActionEvent: handle=$handle ident=$ident actionId=$actionId args=${args?.toString()}")
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

		override fun cds_onPropertyChangedEvent(handle: Int?, ident: String?, propertyName: String?, propertyValue: String?) {
			cdsEventHandler.onPropertyChangedEvent(ident, propertyValue)
		}
	}

	fun onCreate() {
	}

	fun initWidgets() {
		// just in case, but the calendar is hardcoded to go to the month view anyways
		carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach {
			it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = viewMonth.state.id
		}
		viewMonth.initWidgets(viewPermission, viewDay)
		viewDay.initWidgets(viewEvent)
		viewEvent.initWidgets()
	}

	fun navigateNextDestination(currentPosition: LatLong) {
		upcomingDestination.navigateUpcomingDestination(currentPosition)
	}

	fun disconnect() {
		try {
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch ( e: java.io.IOError) {
		} catch (e: RuntimeException) {}
	}
}