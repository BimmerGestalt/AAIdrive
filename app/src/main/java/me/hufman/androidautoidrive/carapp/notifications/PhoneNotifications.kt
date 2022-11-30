package me.hufman.androidautoidrive.carapp.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
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
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.carapp.*
import me.hufman.androidautoidrive.carapp.notifications.views.*
import me.hufman.androidautoidrive.notifications.*
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import me.hufman.androidautoidrive.utils.Utils
import me.hufman.androidautoidrive.utils.removeFirst
import java.util.*

const val TAG = "PhoneNotifications"
const val HMI_CONTEXT_THRESHOLD = 5000L

class PhoneNotifications(val iDriveConnectionStatus: IDriveConnectionStatus, val securityAccess: SecurityAccess, val carAppAssets: CarAppResources, val phoneAppResources: PhoneAppResources, val graphicsHelpers: GraphicsHelpers, val controller: CarNotificationController, val audioPlayer: AudioPlayer, val notificationSettings: NotificationSettings) {
	val notificationListener = PhoneNotificationListener(this)
	val notificationReceiver = NotificationUpdaterControllerIntent.Receiver(notificationListener)
	var notificationBroadcastReceiver: BroadcastReceiver? = null
	val statusbarController: StatusbarControllerWrapper
	val readoutInteractions: ReadoutInteractions
	val carappListener: CarAppListener
	var rhmiHandle: Int = -1
	val carConnection: BMWRemotingServer
	val carAppSwappable: RHMIApplicationSwappable
	val carApp: RHMIApplicationSynchronized
	val amHandle: Int
	val focusTriggerController: FocusTriggerController
	val focusedStateTracker = FocusedStateTracker()
	var hmiContextChangedTime = 0L
	var hmiContextWidgetType: String = ""
	val showNotificationController: ShowNotificationController
	val readHistory = PopupHistory().apply {       // suppress any duplicate New Notification actions
		NotificationsState.cloneNotifications().forEach {
			add(it)     // add the currently shown notifications to suppress popups
		}
	}
	var popupAutoCloser: PopupAutoCloser? = null

	var viewPopup: PopupView                // notification about notification
	val viewList: NotificationListView      // show a list of active notifications
	val viewDetails: DetailsView            // view a notification with actions to do
	val viewPermission: PermissionView      // show a message if permissions are missing
	val stateInput: RHMIState.PlainState    // show a reply input form

	var passengerSeated = false             // whether a passenger is seated

	init {
		val cdsData = CDSDataProvider()
		carappListener = CarAppListener(cdsData)
		carConnection = IDriveConnection.getEtchConnection(iDriveConnectionStatus.host ?: "127.0.0.1", iDriveConnectionStatus.port ?: 8003, carappListener)
		val appCert = carAppAssets.getAppCertificate(iDriveConnectionStatus.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = securityAccess.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)
		carappListener.server = carConnection

		readoutInteractions = ReadoutInteractions(notificationSettings)

		// set up the app in the car
		// synchronized to ensure that events happen after we are done
		synchronized(carConnection) {
			carAppSwappable = RHMIApplicationSwappable(createRhmiApp())
			carApp = RHMIApplicationSynchronized(carAppSwappable, carConnection)
			carappListener.app = carApp
			carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)

			val focusEvent = carApp.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first()
			focusTriggerController = FocusTriggerController(focusEvent) {
				recreateRhmiApp()
			}

			val notificationIconEvent = carApp.events.values.filterIsInstance<RHMIEvent.NotificationIconEvent>().first()
			val id4StatusbarController = ID4StatusbarController(notificationIconEvent, 157)
			statusbarController = StatusbarControllerWrapper(id4StatusbarController)

			val unclaimedStates = LinkedList(carApp.states.values)

			// figure out which views to use
			viewPopup = ID4PopupView(unclaimedStates.removeFirst { ID4PopupView.fits(it) })
			viewList = NotificationListView(unclaimedStates.removeFirst { NotificationListView.fits(it) }, graphicsHelpers, notificationSettings, focusTriggerController, statusbarController, readoutInteractions)
			viewDetails = DetailsView(unclaimedStates.removeFirst { DetailsView.fits(it) }, phoneAppResources, graphicsHelpers, notificationSettings, controller, focusTriggerController, statusbarController, readoutInteractions)
			viewPermission = PermissionView(unclaimedStates.removeFirst { PermissionView.fits(it) })

			stateInput = carApp.states.values.filterIsInstance<RHMIState.PlainState>().first {
				it.componentsList.filterIsInstance<RHMIComponent.Input>().isNotEmpty()
			}

			carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach {
				it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = viewList.state.id
				it.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
					viewList.entryButtonTimestamp = System.currentTimeMillis()
				}
			}

			// set up the AM icon in the "Addressbook"/Communications section
			amHandle = carConnection.am_create("0", "\u0000\u0000\u0000\u0000\u0000\u0002\u0000\u0000".toByteArray())
			carConnection.am_addAppEventHandler(amHandle, "me.hufman.androidautoidrive.notifications")
			showNotificationController = ShowNotificationController(viewDetails, focusTriggerController)
			createAmApp()

			// set up the list
			viewList.initWidgets(showNotificationController, viewPermission)

			// set up the popup
			viewPopup.initWidgets()

			// set up the details view
			viewDetails.initWidgets(viewList, stateInput)

			// set up the permission view
			viewPermission.initWidgets()

			// subscribe to CDS for passenger seat info
			cdsData.setConnection(CDSConnectionEtch(carConnection))
			cdsData.subscriptions.defaultIntervalLimit = 2200
			cdsData.subscriptions[CDS.SENSORS.SEATOCCUPIEDPASSENGER] = {
				val occupied = it["seatOccupiedPassenger"]?.asInt != 0
				passengerSeated = occupied
				readoutInteractions.passengerSeated = occupied
			}
			cdsData.subscriptions[CDS.DRIVING.GEAR] = {
				val GEAR_PARK = 3
				if (it["gear"]?.asInt == GEAR_PARK) {
					viewDetails.lockSpeedLock()
				}
			}
			cdsData.subscriptions[CDS.DRIVING.PARKINGBRAKE] = {
				val APPLIED_BRAKES = setOf(2, 8, 32)
				if (APPLIED_BRAKES.contains(it["parkingBrake"]?.asInt)) {
					viewDetails.lockSpeedLock()
				}
			}
			try {
				// not sure if this works for id4
				cdsData.subscriptions[CDS.HMI.GRAPHICALCONTEXT] = {
					hmiContextChangedTime = System.currentTimeMillis()
					if (it.has("graphicalContext")) {
						val graphicalContext = it.getAsJsonObject("graphicalContext")
						if (graphicalContext.has("widgetType")) {
							hmiContextWidgetType = graphicalContext.getAsJsonPrimitive("widgetType").asString
						}
					}
				}
			} catch (e: BMWRemoting.ServiceException) {}
		}
	}

	/**
	 * Check if we should recreate the app
	 * Is called from within an HMI focused event handler,
	 * so sleeping here is inside a background thread
	 * */
	fun checkRecreate() {
		val interval = 500
		val waitDelay = 20000
		if (focusTriggerController.hasFocusedState && focusedStateTracker.getFocused() == null) {
			for (i in 0..waitDelay step interval) {
				Thread.sleep(interval.toLong())
				if (focusedStateTracker.getFocused() != null) {
					return
				}
			}
			// waited the entire time without getting focused, recreate
			recreateRhmiApp()
		}
	}

	/** creates the app in the car */
	fun createRhmiApp(): RHMIApplication {
		// load the resources
		rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive", BMWRemoting.VersionInfo(0, 1, 0), "me.hufman.androidautoidrive", "me.hufman"))
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppAssets.getUiDescription())
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.TEXTDB, carAppAssets.getTextsDB(iDriveConnectionStatus.brand ?: "common"))
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, carAppAssets.getImagesDB(iDriveConnectionStatus.brand ?: "common"))
		carConnection.rhmi_initialize(rhmiHandle)

		// register for events from the car
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "me.hufman.androidautoidrive.notifications", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "me.hufman.androidautoidrive.notifications", -1, -1)

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

	fun createAmApp() {
		val name = L.NOTIFICATIONS_TITLE
		val carAppImages = Utils.loadZipfile(carAppAssets.getImagesDB(iDriveConnectionStatus.brand ?: "common"))

		val amInfo = mutableMapOf<Int, Any>(
				0 to 145,   // basecore version
				1 to name,  // app name
				2 to (carAppImages["157.png"] ?: ""),
				3 to "Addressbook",   // section
				4 to true,
				5 to 800,   // weight
				8 to viewList.state.id  // mainstateId
		)
		// language translations, dunno which one is which
		for (languageCode in 101..123) {
			amInfo[languageCode] = name
		}

		synchronized(carConnection) {
			carConnection.am_registerApp(amHandle, "androidautoidrive.notifications", amInfo)
		}
	}

	inner class CarAppListener(val cdsEventHandler: CDSEventHandler): BaseBMWRemotingClient() {
		var server: BMWRemotingServer? = null
		var app: RHMIApplication? = null

		fun synced() {
			synchronized(server!!) {
				// the RHMI was definitely initialized, we can continue
			}
		}

		override fun am_onAppEvent(handle: Int?, ident: String?, appId: String?, event: BMWRemoting.AMEvent?) {
			synced()
			viewList.entryButtonTimestamp = System.currentTimeMillis()
			focusTriggerController.focusState(viewList.state, true)
			createAmApp()
		}

		override fun rhmi_onActionEvent(handle: Int?, ident: String?, actionId: Int?, args: MutableMap<*, *>?) {
			Log.w(TAG, "Received rhmi_onActionEvent: handle=$handle ident=$ident actionId=$actionId")
			synced()
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
			synced()

			val state = app?.states?.get(componentId)
			state?.onHmiEvent(eventId, args)

			if (state != null && eventId == 1) {
				val focused = args?.get(4.toByte()) as? Boolean ?: false
				focusedStateTracker.onFocus(state.id, focused)
				checkRecreate()
			}

			val component = app?.components?.get(componentId)
			component?.onHmiEvent(eventId, args)
		}

		override fun cds_onPropertyChangedEvent(handle: Int?, ident: String?, propertyName: String?, propertyValue: String?) {
			cdsEventHandler.onPropertyChangedEvent(ident, propertyValue)
		}
	}

	fun onCreate(context: Context, handler: Handler) {
		Log.i(TAG, "Registering car thread listeners for notifications")
		val notificationBroadcastReceiver = this.notificationBroadcastReceiver ?: object: BroadcastReceiver() {
			override fun onReceive(p0: Context?, p1: Intent?) {
				p1 ?: return
				notificationReceiver.onReceive(p1)
			}
		}
		this.notificationBroadcastReceiver = notificationBroadcastReceiver
		notificationReceiver.register(context, notificationBroadcastReceiver, handler)

		viewList.onCreate(handler)

		popupAutoCloser = PopupAutoCloser(handler, viewPopup)
	}
	fun id5Upgrade(id5StatusbarApp: ID5StatusbarApp) {
		// main app should use id5 for popup access
		viewPopup = id5StatusbarApp.popupView
		// main app should use id5 for statusbar access
		statusbarController.controller = id5StatusbarApp.statusbarController
		// the id5 statusbar can trigger the main app view state
		id5StatusbarApp.showNotificationController = showNotificationController

		// replace the popup autocloser with the new popup state
		val handler = popupAutoCloser?.handler ?: return        // this should be initialized already, but just in case
		popupAutoCloser = PopupAutoCloser(handler, viewPopup)
	}
	fun onDestroy(context: Context) {
		val notificationReceiver = this.notificationBroadcastReceiver
		if (notificationReceiver != null) {
			try {
				context.unregisterReceiver(notificationReceiver)
			} catch (e: IllegalArgumentException) {}
		}
	}
	fun disconnect() {
		try {
			Log.i(TAG, "Trying to shut down etch connection")
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch ( e: java.io.IOError) {
		} catch (e: RuntimeException) {}
	}

	/** All open, so that we can mock them in tests */
	open inner class PhoneNotificationListener(val phoneNotifications: PhoneNotifications): NotificationUpdaterController {
		override fun onNewNotification(key: String) {
			val sbn = NotificationsState.getNotificationByKey(key) ?: return
			onNotification(sbn)
		}

		fun onNotification(sbn: CarNotification) {
			Log.i(TAG, "Received a new notification to show in the car: $sbn")

			// only show if we haven't popped it before
			val alreadyShown = readHistory.contains(sbn)
			readHistory.add(sbn)
			if (!alreadyShown) {
				val currentlyPopped = sbn.equalsKey(viewPopup.currentNotification)
				val currentlyReading = viewDetails.visible && sbn.equalsKey(viewDetails.selectedNotification)
				val currentlyInputing = hmiContextWidgetType.lowercase(Locale.ROOT).contains("speller") ||
						hmiContextWidgetType.lowercase(Locale.ROOT).contains("keyboard") ||
						focusedStateTracker.isFocused[stateInput.id] == true
				val timeSinceContextChange = System.currentTimeMillis() - hmiContextChangedTime
				val userActivelyInteracting = timeSinceContextChange < HMI_CONTEXT_THRESHOLD
				if (notificationSettings.shouldPopup(passengerSeated) && (currentlyPopped || (!currentlyReading && !currentlyInputing && !userActivelyInteracting))) {
					viewPopup.showNotification(sbn)
					popupAutoCloser?.start()
				} else if (!currentlyPopped && !currentlyReading) {
					// only show the statusbar icon if we didn't pop it up
					viewList.showNotification(sbn)
				}

				val played = if (notificationSettings.shouldPlaySound()) {
					audioPlayer.playRingtone(sbn.soundUri)
				} else false

				if (notificationSettings.shouldReadoutNotificationPopup(passengerSeated) && played) {
					Thread.sleep(3000)
				}
				readoutInteractions.triggerPopupReadout(sbn)
			}
		}

		override fun onUpdatedList() {
			Log.i(TAG, "Received a list of new notifications to show")
			viewList.gentlyUpdateNotificationList()

			viewDetails.redraw()

			// clear out any popped notifications that don't exist anymore
			val currentNotifications = NotificationsState.cloneNotifications()
			readHistory.retainAll(currentNotifications)

			// if the notification we popped up disappeared, clear the popup
			if (currentNotifications.find { it.key == viewPopup.currentNotification?.key } == null) {
				viewPopup.hideNotification()
			}

			// cancel the notification readout if it goes away
			val currentReadout = readoutInteractions.currentNotification
			if (currentNotifications.find { it.key == currentReadout?.key } == null) {
				readoutInteractions.cancel()
			}

			// remove any removed notifications from the statusbar controller
			statusbarController.retainAll(currentNotifications)
		}
	}
}

class ShowNotificationController(val detailsView: DetailsView, val focusTriggerController: FocusTriggerController) {
	/**
	 * If the source action is tied to a widget with a linked HmiAction,
	 * setting the HMIAction's destination is preferred and 100% reliable
	 */
	fun showFromHmiAction(action: RHMIAction.HMIAction?, notification: CarNotification?) {
		notification?.also { detailsView.selectedNotification = it }
		action?.getTargetModel()?.asRaIntModel()?.value = detailsView.state.id
	}

	/**
	 * For other uses, this focus event may work less reliably
	 */
	fun showFromFocusEvent(notification: CarNotification?, recreate: Boolean): Boolean {
		notification?.also { detailsView.selectedNotification = it }
		return focusTriggerController.focusState(detailsView.state, recreate)
	}

	fun getSelectedNotification(): CarNotification? {
		return detailsView.selectedNotification
	}
}