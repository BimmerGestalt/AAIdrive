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
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.RHMIUtils
import me.hufman.androidautoidrive.carapp.notifications.views.DetailsView
import me.hufman.androidautoidrive.carapp.notifications.views.NotificationListView
import me.hufman.androidautoidrive.carapp.notifications.views.PopupView
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationIdempotent
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationSynchronized
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import me.hufman.idriveconnectionkit.rhmi.*
import org.json.JSONException
import org.json.JSONObject
import java.lang.RuntimeException
import java.util.*

const val TAG = "PhoneNotifications"

class PhoneNotifications(securityAccess: SecurityAccess, val carAppAssets: CarAppResources, val phoneAppResources: PhoneAppResources, val graphicsHelpers: GraphicsHelpers, val controller: CarNotificationController, val appSettings: MutableAppSettings) {
	companion object {
		const val INTENT_UPDATE_NOTIFICATIONS = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications.UPDATE_NOTIFICATIONS"
		const val INTENT_NEW_NOTIFICATION = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications.NEW_NOTIFICATION"
		const val EXTRA_NOTIFICATION = "me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications.EXTRA_NOTIFICATION"
	}
	val notificationListener = PhoneNotificationListener()
	var notificationReceiver: PhoneNotificationUpdate? = null
	val carappListener = CarAppListener()
	val carConnection: BMWRemotingServer
	val carApp: RHMIApplicationSynchronized
	val viewPopup: PopupView                // notification about notification
	val viewList: NotificationListView      // show a list of active notifications
	val viewDetails: DetailsView            // view a notification with actions to do
	val stateInput: RHMIState.PlainState    // show a reply input form

	var passengerSeated = false             // whether a passenger is seated
	var lastPopup: CarNotification? = null  // the last notification that we popped up

	init {
		carConnection = IDriveConnection.getEtchConnection(IDriveConnectionListener.host ?: "127.0.0.1", IDriveConnectionListener.port ?: 8003, carappListener)
		val appCert = carAppAssets.getAppCertificate(IDriveConnectionListener.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = securityAccess.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)
		carappListener.server = carConnection

		// create the app in the car
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive", BMWRemoting.VersionInfo(0, 1, 0), "me.hufman.androidautoidrive", "me.hufman"))
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppAssets.getUiDescription())
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.TEXTDB, carAppAssets.getTextsDB("common"))
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, carAppAssets.getImagesDB("common"))
		carConnection.rhmi_initialize(rhmiHandle)

		// get the list of capabilities, to decide what settings to show
		val capabilities = carConnection.rhmi_getCapabilities("", rhmiHandle)
				.filter { it.key is String && it.value is String }
				.mapKeys { it.key as String }
				.mapValues { it.value as String }
		val notificationSettings = NotificationSettings(capabilities, appSettings)

		// set up the app in the car
		carApp = RHMIApplicationSynchronized(RHMIApplicationIdempotent(RHMIApplicationEtch(carConnection, rhmiHandle)))
		carappListener.app = carApp
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)

		val unclaimedStates = LinkedList(carApp.states.values)

		// figure out which views to use
		viewPopup = PopupView(unclaimedStates.removeFirst { PopupView.fits(it) }, phoneAppResources, PopupHistory())
		viewList = NotificationListView(unclaimedStates.removeFirst { NotificationListView.fits(it) }, phoneAppResources, graphicsHelpers, notificationSettings)
		viewDetails = DetailsView(unclaimedStates.removeFirst { DetailsView.fits(it) }, phoneAppResources, graphicsHelpers, controller)

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
		val interestingProperties = mapOf("76" to "sensors.seatOccupiedPassenger",
				"37" to "driving.gear",
				"40" to "driving.parkingBrake")
		interestingProperties.entries.forEach {
			val ident = it.key
			val name = it.value
			carConnection.cds_addPropertyChangedEventHandler(cdsHandle, name, ident, 5000)
			carConnection.cds_getPropertyAsync(cdsHandle, ident, name)
		}

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
				carApp.runSynchronized {
					server?.rhmi_ackActionEvent(handle, actionId, 1, true)
				}
			} catch (e: RHMIActionAbort) {
				// Action handler requested that we don't claim success
				carApp.runSynchronized {
					server?.rhmi_ackActionEvent(handle, actionId, 1, false)
				}
			} catch (e: Exception) {
				Log.e(TAG, "Exception while calling onActionEvent handler! $e")
				carApp.runSynchronized {
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

		fun loadJSON(str: String?): JSONObject? {
			if (str == null) return null
			try {
				return JSONObject(str)
			} catch (e: JSONException) {
				return null
			}
		}
		override fun cds_onPropertyChangedEvent(handle: Int?, ident: String?, propertyName: String?, propertyValue: String?) {
			if (propertyValue == null || loadJSON(propertyValue) == null) {
				return
			}
			val propertyData = loadJSON(propertyValue) ?: return

			if (propertyName == "sensors.seatOccupiedPassenger") {
				passengerSeated = propertyData.getInt("seatOccupiedPassenger") != 0
			}
			if (propertyName == "driving.gear") {
				val GEAR_PARK = 3
				if (propertyData.getInt("gear") == GEAR_PARK) {
					viewDetails.lockSpeedLock()
				}
			}
			if (propertyName == "driving.parkingBrake") {
				val APPLIED_BRAKES = setOf(2, 8, 32)
				if (APPLIED_BRAKES.contains(propertyData.getInt("parkingBrake"))) {
					viewDetails.lockSpeedLock()
				}
			}
		}
	}

	fun onCreate(context: Context, handler: Handler) {
		Log.i(TAG, "Registering car thread listeners for notifications")
		val notificationReceiver = this.notificationReceiver ?:
			PhoneNotificationUpdate(notificationListener)
		this.notificationReceiver = notificationReceiver
		context.registerReceiver(notificationReceiver, IntentFilter(INTENT_NEW_NOTIFICATION), null, handler)
		context.registerReceiver(notificationReceiver, IntentFilter(INTENT_UPDATE_NOTIFICATIONS), null, handler)

		viewList.onCreate(handler)
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
			Log.i(TAG, "Received a new notification to show in the car: $sbn")
			viewList.showStatusBarIcon()

			if (AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP].toBoolean() &&
				(AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER].toBoolean() ||
					!passengerSeated)
			) {
				lastPopup = sbn

				if (!sbn.equalsKey(viewDetails.selectedNotification)) {
					viewPopup.showNotification(sbn)
				}
			}
		}

		open fun updateNotificationList() {
			Log.i(TAG, "Received a list of new notifications to show")
			viewList.gentlyUpdateNotificationList()

			viewDetails.redraw()

			// clear out any popped notifications that don't exist anymore
			val currentNotifications = NotificationsState.cloneNotifications()
			viewPopup.popupHistory.retainAll(currentNotifications)

			// if the notification we popped up disappeared, clear the popup
			if (currentNotifications.find { it.key == lastPopup?.key } == null) {
				viewPopup.hideNotification()
			}

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