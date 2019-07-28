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
import me.hufman.androidautoidrive.DeferredUpdate
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.carapp.RHMIApplicationSynchronized
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
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
	val notificationListener = PhoneNotificationUpdate(PhoneNotificationListener())
	val carappListener = CarAppListener()
	val carConnection: BMWRemotingServer
	val carApp: RHMIApplication
	val statePopup: RHMIState.PopupState    // notification about notification
	val stateList: RHMIState.PlainState     // show a list of active notifications
	val stateView: RHMIState.ToolbarState   // view a notification with actions to do
	val stateInput: RHMIState.PlainState    // show a reply input form
	val notificationListView: RHMIComponent.List    // the list component of notifications
	val shownNotifications = ArrayList<CarNotification>()   // which notifications are showing
	val notificationListData = object: RHMIListAdapter<CarNotification>(3, shownNotifications) {
		override fun convertRow(index: Int, item: CarNotification): Array<Any> {
			val icon = phoneAppResources.getBitmap(phoneAppResources.getIconDrawable(item.icon), 48, 48)
			val text = if (item.summary != null) "${item.title}\n${item.summary}" else "${item.title}\n${item.text}"
			return arrayOf(icon, "", text)
		}
	}
	val emptyListData = RHMIModel.RaListModel.RHMIListConcrete(3).apply {
		addRow(arrayOf("", "", L.NOTIFICATIONS_EMPTY_LIST))
	}

	var listFocused = false                 // whether the notification list is showing
	var passengerSeated = false             // whether a passenger is seated
	val INTERACTION_DEBOUNCE_MS = 5000              // how long to wait after lastInteractionTime to update the list
	var lastInteractionTime: Long = 0             // timestamp when the user last navigated in the main list
	var lastInteractionIndex: Int = -1       // what index the user last selected

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

		// figure out which views to use
		statePopup = carApp.states.values.filterIsInstance<RHMIState.PopupState>().first{
			//it.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty()
			true
		}
		stateList = carApp.states.values.filterIsInstance<RHMIState.PlainState>().first{
			it.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty()
		}
		stateView = carApp.states.values.filterIsInstance<RHMIState.ToolbarState>().first{
			it.toolbarComponentsList[0].asToolbarButton()?.imageModel == 0   // look for the paging state, it has a list
		}
		stateInput = carApp.states.values.filterIsInstance<RHMIState.PlainState>().first{
			it.componentsList.filterIsInstance<RHMIComponent.Input>().isNotEmpty()
		}

		carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach {
			it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = stateList.id
		}

		// set up the list
		stateList.getTextModel()?.asRaDataModel()?.value = L.NOTIFICATIONS_TITLE
		stateList.componentsList.forEach { it.setVisible(false) }
		notificationListView = stateList.componentsList.filterIsInstance<RHMIComponent.List>().first()
		notificationListView.setVisible(true)
		notificationListView.setProperty(6, "55,0,*")
		notificationListView.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index ->
			val notification = shownNotifications.getOrNull(index)
			if (notification != null) {
				// set the list to go into the state
				notificationListView.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = stateView.id

				val actionId = notificationListView.getAction()?.asRAAction()?.id
				carConnection.rhmi_ackActionEvent(rhmiHandle, actionId ?: 0, 1, true)   // start screen transition
				NotificationsState.selectedNotification = notification
				updateNotificationView()    // because updating this view would delay the transition too long
			} else {
				notificationListView.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = 0
			}
		}
		notificationListView.getSelectAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback {
			if (it != lastInteractionIndex) {
				lastInteractionIndex = it
				lastInteractionTime = System.currentTimeMillis()
			}
		}

		// set up the popup
		statePopup.componentsList.filterIsInstance<RHMIComponent.Label>().lastOrNull()?.setSelectable(true)

		// set up the view
		stateView.componentsList.forEach { it.setVisible(false) }
		stateView.componentsList.forEach { it.setEnabled(false) }
		stateView.componentsList.filterIsInstance<RHMIComponent.List>().firstOrNull()?.apply {
			// text
			setVisible(true)
			setProperty(6, "55,0,*")
		}
		var buttons = ArrayList(stateView.toolbarComponentsList).filterIsInstance<RHMIComponent.ToolbarButton>().filter { it.action > 0}
		stateView.toolbarComponentsList.forEach { it.setVisible(false) }
		buttons[0].getImageModel()?.asImageIdModel()?.imageId = 150
		buttons[0].setVisible(true)
		buttons[0].setSelectable(true)
		buttons.subList(1, 6).forEach {
			it.getImageModel()?.asImageIdModel()?.imageId = 158
		}

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

			// if the notification list is changing its focus state
			if (componentId == stateList.id &&
					eventId == 1  // FOCUS event
				) {
				listFocused = args?.get(4.toByte()) as? Boolean == true
				// if the user opened the notification list
				if (listFocused) {
					updateNotificationList()
				}
			}
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
		if (handler != null) {
			context.registerReceiver(notificationListener, IntentFilter(INTENT_NEW_NOTIFICATION), null, handler)
			context.registerReceiver(notificationListener, IntentFilter(INTENT_UPDATE_NOTIFICATIONS), null, handler)
		} else {
			context.registerReceiver(notificationListener, IntentFilter(INTENT_NEW_NOTIFICATION))
			context.registerReceiver(notificationListener, IntentFilter(INTENT_UPDATE_NOTIFICATIONS))

		}
	}
	fun onDestroy(context: Context) {
		context.unregisterReceiver(notificationListener)
		try {
			Log.i(TAG, "Trying to shut down etch connection")
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch ( e: java.io.IOError) {
		} catch (e: RuntimeException) {}
	}

	fun updateNotificationList() {
		synchronized(NotificationsState.notifications) {
			shownNotifications.clear()
			shownNotifications.addAll(NotificationsState.notifications)
		}

		if (NotificationsState.notifications.isEmpty()) {
			notificationListView.getModel()?.value = emptyListData
		} else {
			notificationListView.getModel()?.value = notificationListData
		}
	}

	fun updateNotificationView() {
		// clear the toolbar
		var buttons = ArrayList(stateView.toolbarComponentsList).filterIsInstance<RHMIComponent.ToolbarButton>().filter { it.action > 0}
		buttons.forEach { it.setEnabled(false) }

		if (NotificationsState.selectedNotification == null) {
			// if the selected notification is invalid
			val listData = RHMIModel.RaListModel.RHMIListConcrete(3)
			listData.addRow(arrayOf("", "", ""))
			val listWidget = stateView.componentsList.filterIsInstance<RHMIComponent.List>().firstOrNull() ?: return
			listWidget.getModel()?.value = listData
			stateView.getTextModel()?.asRaDataModel()?.value = ""
			// perhaps there's a way to close the window if it's open
			return
		}

		val notification = NotificationsState.selectedNotification ?: return
		val listData = RHMIModel.RaListModel.RHMIListConcrete(3)
		val icon = phoneAppResources.getBitmap(phoneAppResources.getIconDrawable(notification.icon), 48, 48)
		val appname = phoneAppResources.getAppName(notification.packageName)
		listData.addRow(arrayOf(icon, "", appname))
		listData.addRow(arrayOf("", "", notification.title ?: ""))
		(notification.text ?: "").split(Regex("\n")).filter {
			it.isNotEmpty()
		}.forEach {
			listData.addRow(arrayOf("", "", it))
		}

		stateView.getTextModel()?.asRaDataModel()?.value = notification.title ?: appname

		val listWidget = stateView.componentsList.filterIsInstance<RHMIComponent.List>().firstOrNull() ?: return
		listWidget.getModel()?.value = listData

		val clearButton = buttons[0]

		if (notification.isClearable) {
			clearButton.setEnabled(true)
			clearButton.getTooltipModel()?.asRaDataModel()?.value = L.NOTIFICATION_CLEAR_ACTION
			clearButton.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = stateList.id
			clearButton.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
				controller.clear(notification)
				Thread.sleep(100)
				updateNotificationList()
				carApp.events.values.filterIsInstance<RHMIEvent.FocusEvent>().firstOrNull()?.triggerEvent(mapOf(0 to listWidget.id))
			}
		} else {
			clearButton.setEnabled(false)
		}

		(0..4).forEach {i ->
			val action = notification.actions.getOrNull(i)
			var button = buttons[1+i]
			if (action == null) {
				button.setVisible(false)
				button.setEnabled(false)
				button.setSelectable(false)
				button.getAction()?.asRAAction()?.rhmiActionCallback = null // don't leak memory
			} else {
				if (action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
					// TODO Implement <input> view reply
					button.setEnabled(false)
				} else {
					button.setEnabled(true)
				}
				button.setSelectable(true)
				button.setVisible(true)
				button.getTooltipModel()?.asRaDataModel()?.value = action.title.toString()
				button.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = stateList.id  // usually the action will clear the notification
				button.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
					controller.action(notification, action.title.toString())
				}
			}
		}

		val focusEvent = carApp.events.values.filterIsInstance<RHMIEvent.FocusEvent>().firstOrNull()
		focusEvent?.getTargetModel()?.asRaDataModel()?.value = clearButton.id.toString()
		if (notification.isClearable) {
			focusEvent?.triggerEvent(mapOf(0 to clearButton.id))
		} else {
			focusEvent?.triggerEvent(mapOf(0 to buttons[1].id))
		}
	}

	/** All open, so that we can mock them in tests */
	open inner class PhoneNotificationListener {
		open fun onNotification(sbn: CarNotification) {
			Log.i(TAG, "Received a new notification to show in the car")
			val appname = phoneAppResources.getAppName(sbn.packageName)
			val titleLabel = statePopup.getTextModel()?.asRaDataModel() ?: return
			val bodyLabel1 = statePopup.componentsList.filterIsInstance<RHMIComponent.Label>().firstOrNull()?.getModel()?.asRaDataModel() ?: return
			val bodyLabel2 = statePopup.componentsList.filterIsInstance<RHMIComponent.Label>().getOrNull(1)?.getModel()?.asRaDataModel() ?: return
			titleLabel.value = appname
			bodyLabel1.value = sbn.title.toString()
			bodyLabel2.value = sbn.text?.split(Regex("\n"))?.lastOrNull() ?: sbn.summary ?: ""
			if (AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP].toBoolean() &&
					(AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER].toBoolean() ||
							!passengerSeated)
			) {
				carApp.events.values.filterIsInstance<RHMIEvent.PopupEvent>().firstOrNull { it.getTarget() == statePopup }?.triggerEvent()
			}
		}

		open fun updateNotificationList() {
			Log.i(TAG, "Received a list of new notifications to show")
			DeferredUpdate.trigger("PhoneNotificationList", {
				val interactionTimeAgo = System.currentTimeMillis() - lastInteractionTime
				val interactionTimeRemaining = INTERACTION_DEBOUNCE_MS - interactionTimeAgo
				interactionTimeRemaining
			}, {

				if (listFocused) {
					Log.i(TAG, "Updating list of notifications")
					this@PhoneNotifications.updateNotificationList()
				} else {
					Log.i(TAG, "Notification list is not on screen, skipping update")
				}

				// check if we should clear an old notification from the NotificationView
				val currentNotifications = LinkedList(NotificationsState.notifications) // safe-from-other-thread-mutation view
				val selectedNotification = NotificationsState.selectedNotification
				if (NotificationsState.selectedNotification != null && !currentNotifications.map { it.key }.contains(selectedNotification?.key)) {
					NotificationsState.selectedNotification = null
					updateNotificationView()
				}
			})
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