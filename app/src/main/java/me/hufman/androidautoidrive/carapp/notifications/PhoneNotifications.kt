package me.hufman.androidautoidrive.carapp.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.carapp.RHMIApplicationSynchronized
import me.hufman.androidautoidrive.carapp.notifications.views.DetailsView
import me.hufman.androidautoidrive.carapp.notifications.views.NotificationListView
import me.hufman.androidautoidrive.carapp.notifications.views.PopupView
import me.hufman.androidautoidrive.removeFirst
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.SecurityService
import me.hufman.idriveconnectionkit.rhmi.*
import org.json.JSONException
import org.json.JSONObject
import java.lang.RuntimeException
import java.util.*

const val TAG = "PhoneNotifications"

class PhoneNotifications(val carAppAssets: CarAppResources, val phoneAppResources: PhoneAppResources, val controller: CarNotificationController) {
	companion object {
		const val INTENT_UPDATE_NOTIFICATIONS = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications.UPDATE_NOTIFICATIONS"
		const val INTENT_NEW_NOTIFICATION = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications.NEW_NOTIFICATION"
		const val EXTRA_NOTIFICATION = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications.EXTRA_NOTIFICATION"
	}
	val notificationListener = PhoneNotificationListener()
	var notificationReceiver: PhoneNotificationUpdate? = null
	val carappListener = CarAppListener()
	val carConnection: BMWRemotingServer
	val carApp: RHMIApplication
	val viewPopup: PopupView                // notification about notification
	val viewList: NotificationListView      // show a list of active notifications
	val viewDetails: DetailsView            // view a notification with actions to do
	val stateInput: RHMIState.PlainState    // show a reply input form

	var passengerSeated = false             // whether a passenger is seated

	init {
		carConnection = IDriveConnection.getEtchConnection(IDriveConnectionListener.host ?: "127.0.0.1", IDriveConnectionListener.port ?: 8003, carappListener)
		val appCert = carAppAssets.getAppCertificate(IDriveConnectionListener.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = SecurityService.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)
		carappListener.server = carConnection

		// create the app in the car
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive", BMWRemoting.VersionInfo(0, 1, 0), "me.hufman.androidautoidrive", "me.hufman"))
		carConnection.rhmi_setResource(rhmiHandle, carAppAssets.getUiDescription()?.readBytes(), BMWRemoting.RHMIResourceType.DESCRIPTION)
		carConnection.rhmi_setResource(rhmiHandle, carAppAssets.getTextsDB("common")?.readBytes(), BMWRemoting.RHMIResourceType.TEXTDB)
		carConnection.rhmi_setResource(rhmiHandle, carAppAssets.getImagesDB("common")?.readBytes(), BMWRemoting.RHMIResourceType.IMAGEDB)
		carConnection.rhmi_initialize(rhmiHandle)

		// set up the app in the car
		carApp = RHMIApplicationSynchronized(RHMIApplicationEtch(carConnection, rhmiHandle))
		carappListener.app = carApp
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)

		val unclaimedStates = LinkedList(carApp.states.values)

		// figure out which views to use
		viewPopup = PopupView(unclaimedStates.removeFirst { PopupView.fits(it) }, phoneAppResources)
		viewList = NotificationListView(unclaimedStates.removeFirst { NotificationListView.fits(it) }, phoneAppResources)
		viewDetails = DetailsView(unclaimedStates.removeFirst { DetailsView.fits(it) }, phoneAppResources, controller)

		stateInput = carApp.states.values.filterIsInstance<RHMIState.PlainState>().first{
			it.componentsList.filterIsInstance<RHMIComponent.Input>().isNotEmpty()
		}

		carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach {
			it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = viewList.state.id
		}

		// set up the list
		viewList.initWidgets(viewDetails)

		// set up the popup
		viewPopup.initWidgets()

		// set up the details view
		viewDetails.initWidgets(viewList)

		// subscribe to CDS for passenger seat info
		val cdsHandle = carConnection.cds_create()
		carConnection.cds_addPropertyChangedEventHandler(cdsHandle, "sensors.seatOccupiedPassenger", "76", 5000)
		carConnection.cds_getPropertyAsync(cdsHandle, "76", "sensors.seatOccupiedPassenger")

		// register for events from the car
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "me.hufman.androidautoidrive.notifications", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "me.hufman.androidautoidrive.notifications", -1, -1)
	}

	inner class CarAppListener: BaseBMWRemotingClient() {
		var server: BMWRemotingServer? = null
		var app: RHMIApplication? = null
		override fun rhmi_onActionEvent(handle: Int?, ident: String?, actionId: Int?, args: MutableMap<*, *>?) {
			Log.w(TAG, "Received rhmi_onActionEvent: handle=$handle ident=$ident actionId=$actionId")
			try {
				app?.actions?.get(actionId)?.asRAAction()?.rhmiActionCallback?.onActionEvent(args)
			} catch (e: Exception) {
				Log.e(TAG, "Exception while calling onActionEvent handler! $e")
			}
			server?.rhmi_ackActionEvent(handle, actionId, 1, true)
		}

		override fun rhmi_onHmiEvent(handle: Int?, ident: String?, componentId: Int?, eventId: Int?, args: MutableMap<*, *>?) {
			val msg = "Received rhmi_onHmiEvent: handle=$handle ident=$ident componentId=$componentId eventId=$eventId args=${args?.toString()}"
			Log.w(TAG, msg)

			val state = app?.states?.get(componentId)
			state?.onHmiEvent(eventId, args)

			val component = app?.components?.get(componentId)
			component?.onHmiEvent(eventId, args)
		}

		fun loadJSON(str: String?): JSONObject? {
			if (str == null) return null
			try {
				return JSONObject(str)
			} catch (e: JSONException) {
				return null
			}
		}
		override fun cds_onPropertyChangedEvent(handle: Int?, ident: String?, propertyName: String?, propertyValue: String?) {
			if (propertyName == "sensors.seatOccupiedPassenger" && propertyValue != null && loadJSON(propertyValue) != null) {
				val propertyData = loadJSON(propertyValue) ?: return
				passengerSeated = propertyData.getInt("seatOccupiedPassenger") != 0
			}
		}
	}

	fun onCreate(context: Context, handler: Handler? = null) {
		Log.i(TAG, "Registering car thread listeners for notifications")
		val notificationReceiver = this.notificationReceiver ?:
			PhoneNotificationUpdate(notificationListener)
		this.notificationReceiver = notificationReceiver
		if (handler != null) {
			context.registerReceiver(notificationReceiver, IntentFilter(INTENT_NEW_NOTIFICATION), null, handler)
			context.registerReceiver(notificationReceiver, IntentFilter(INTENT_UPDATE_NOTIFICATIONS), null, handler)
		} else {
			context.registerReceiver(notificationReceiver, IntentFilter(INTENT_NEW_NOTIFICATION))
			context.registerReceiver(notificationReceiver, IntentFilter(INTENT_UPDATE_NOTIFICATIONS))
		}
	}
	fun onDestroy(context: Context) {
		val notificationReceiver = this.notificationReceiver
		if (notificationReceiver != null) {
			context.unregisterReceiver(notificationReceiver)
		}
		try {
			Log.i(TAG, "Trying to shut down etch connection")
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch ( e: java.io.IOError) {
		} catch (e: RuntimeException) {}
	}

	/** All open, so that we can mock them in tests */
	open inner class PhoneNotificationListener {
		open fun onNotification(sbn: CarNotification) {
			Log.i(TAG, "Received a new notification to show in the car")
			if (AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP].toBoolean() &&
					(AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER].toBoolean() ||
							!passengerSeated)
			) {
				if (!sbn.equalsKey(NotificationsState.selectedNotification)) {
					viewPopup.showNotification(sbn)
				}
			}
		}

		open fun updateNotificationList() {
			Log.i(TAG, "Received a list of new notifications to show")
			viewList.gentlyUpdateNotificationList()

			viewDetails.redraw()
		}
	}

	class PhoneNotificationUpdate(val listener: PhoneNotificationListener): BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {

			if (intent != null && intent.action == INTENT_NEW_NOTIFICATION) {
				val notificationKey = intent.getStringExtra(EXTRA_NOTIFICATION)
				if (notificationKey != null) {
					val notification = NotificationsState.getNotificationByKey(notificationKey)
					if (notification != null)
						listener.onNotification(notification)
				}
			}
			if (intent != null && intent.action == INTENT_UPDATE_NOTIFICATIONS) {
				listener.updateNotificationList()
			}
		}

	}
}