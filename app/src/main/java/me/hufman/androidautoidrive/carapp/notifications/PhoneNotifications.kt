package me.hufman.androidautoidrive.carapp.notifications

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.androidautoidrive.DeferredUpdate
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.Utils
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.SecurityService
import me.hufman.idriveconnectionkit.rhmi.*
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
	val carApp: RHMIApplicationEtch
	val statePopup: RHMIState.PopupState    // notification about notification
	val stateList: RHMIState.PlainState     // show a list of active notifications
	val stateView: RHMIState.ToolbarState   // view a notification with actions to do
	val stateInput: RHMIState.PlainState    // show a reply input form
	var listFocused = false                 // whether the notification list is showing
	val INTERACTION_DEBOUNCE_MS = 5000              // how long to wait after lastInteractionTime to update the list
	var lastInteractionTime: Long = 0             // timestamp when the user last navigated in the main list
	var interactionDebounceTimer: Timer? = null    // a thread to refresh the list after an update

	init {
		carConnection = IDriveConnection.getEtchConnection(IDriveConnectionListener.host ?: "127.0.0.1", IDriveConnectionListener.port ?: 8003, carappListener)
		val appCert = carAppAssets.getAppCertificate(IDriveConnectionListener.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = SecurityService.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)
		carappListener.server = carConnection

		// create the app in the car
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive", BMWRemoting.VersionInfo(0, 1, 0), "me.hufman.androidautoidrive", "me.hufman"))
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "me.hufman.androidautoidrive.notifications", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "me.hufman.androidautoidrive.notifications", -1, -1)
		carConnection.rhmi_setResource(rhmiHandle, carAppAssets.getUiDescription()?.readBytes(), BMWRemoting.RHMIResourceType.DESCRIPTION)
		carConnection.rhmi_setResource(rhmiHandle, carAppAssets.getTextsDB("common")?.readBytes(), BMWRemoting.RHMIResourceType.TEXTDB)
		carConnection.rhmi_setResource(rhmiHandle, carAppAssets.getImagesDB("common")?.readBytes(), BMWRemoting.RHMIResourceType.IMAGEDB)
		carConnection.rhmi_initialize(rhmiHandle)

		// set up the app in the car
		carApp = RHMIApplicationEtch(carConnection, rhmiHandle)
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
		stateList.componentsList.forEach { it?.setVisible(false) }
		val notificationListView = stateList.componentsList.filterIsInstance<RHMIComponent.List>().firstOrNull()
		notificationListView?.setVisible(true)
		notificationListView?.setProperty(6, "55,0,*")
		notificationListView?.getAction()?.asRAAction()?.rhmiActionCallback = object: RHMIAction.RHMIActionCallback {
			override fun onActionEvent(args: Map<*, *>?) {
				val index = Utils.etchAsInt(args?.get(1.toByte()))
				val notification = NotificationsState.notifications.getOrNull(index)
				if (notification != null) {
					notificationListView?.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = stateView.id
					NotificationsState.selectedNotification = notification
					updateNotificationView()
				} else {
					notificationListView?.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = 0
				}
			}
		}
		notificationListView?.getSelectAction()?.asRAAction()?.rhmiActionCallback = object: RHMIAction.RHMIActionCallback {
			override fun onActionEvent(args: Map<*, *>?) {
				lastInteractionTime = System.currentTimeMillis()
			}
		}

		// set up the popup
		statePopup.componentsList.filterIsInstance<RHMIComponent.Label>().lastOrNull()?.setSelectable(true)

		// set up the view
		stateView.toolbarComponentsList.forEach { it.setVisible(false) }
		stateView.componentsList.forEach { it.setVisible(false) }
		stateView.componentsList.forEach { it.setEnabled(false) }
		stateView.componentsList.filterIsInstance<RHMIComponent.List>().firstOrNull()?.apply {
			// text
			setVisible(true)
			setProperty(6, "55,0,*")
		}
	}

	inner class CarAppListener: BaseBMWRemotingClient() {
		var server: BMWRemotingServer? = null
		var app: RHMIApplication? = null
		override fun rhmi_onActionEvent(handle: Int?, ident: String?, actionId: Int?, args: MutableMap<*, *>?) {
			Log.w(TAG, "Received rhmi_onActionEvent: handle=$handle ident=$ident actionId=$actionId")
			try {
				app?.actions?.get(actionId)?.asRAAction()?.rhmiActionCallback?.onActionEvent(args)
			} catch (e: Exception) {
				Log.e(TAG, "Exception while calling onActionEvent handler!", e)
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
	}

	fun onCreate(context: Context) {
		LocalBroadcastManager.getInstance(context).registerReceiver(notificationListener, IntentFilter(INTENT_NEW_NOTIFICATION))
		LocalBroadcastManager.getInstance(context).registerReceiver(notificationListener, IntentFilter(INTENT_UPDATE_NOTIFICATIONS))
	}
	fun onDestroy(context: Context) {
		LocalBroadcastManager.getInstance(context).unregisterReceiver(notificationListener)

	}

	fun updateNotificationList() {
		var foundSelectedNotification = false
		val listData = RHMIModel.RaListModel.RHMIListConcrete(3)
		synchronized(NotificationsState.notifications) {
			NotificationsState.notifications.forEach {
				val icon = phoneAppResources.getBitmap(phoneAppResources.getIconDrawable(it.icon), 48, 48)
				val text = if (it.summary != null) "${it.title}\n${it.summary}" else "${it.title}\n${it.text}"
				listData.addRow(arrayOf(icon, "", text))
			}
			if (NotificationsState.notifications.isEmpty()) {
				listData.addRow(arrayOf("", "", "No Notifications"))
			}

			foundSelectedNotification = NotificationsState.notifications.any {
				it.key == NotificationsState.selectedNotification?.key
			}
		}
		val listWidget = stateList.componentsList.filterIsInstance<RHMIComponent.List>().firstOrNull() ?: return
		listWidget.getModel()?.value = listData

		if (NotificationsState.selectedNotification != null && ! foundSelectedNotification) {
			NotificationsState.selectedNotification = null
			updateNotificationView()
		}
	}

	fun updateNotificationView() {
		// clear the toolbar
		var buttons = ArrayList(stateView.toolbarComponentsList).filterIsInstance<RHMIComponent.ToolbarButton>().filter { it.action > 0}
		buttons.forEach { it.setEnabled(false) }
		buttons.forEach { it.getImageModel()?.asImageIdModel()?.imageId = 0; it.setVisible(true) }

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
		listData.addRow(arrayOf("", "", notification.text ?: ""))

		stateView.getTextModel()?.asRaDataModel()?.value = appname

		val listWidget = stateView.componentsList.filterIsInstance<RHMIComponent.List>().firstOrNull() ?: return
		listWidget.getModel()?.value = listData

		val clearButton = buttons[0]

		if (notification.isClearable) {
			clearButton.setEnabled(true)
			clearButton.setSelectable(true)
			clearButton.getTooltipModel()?.asRaDataModel()?.value = "Clear"
			clearButton.getImageModel()?.asImageIdModel()?.imageId = 150
			clearButton.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = stateList.id
			clearButton.getAction()?.asRAAction()?.rhmiActionCallback = object: RHMIAction.RHMIActionCallback {
				override fun onActionEvent(args: Map<*, *>?) {
					controller.clear(notification)
					Thread.sleep(200)
					updateNotificationList()
					carApp.events.values.filterIsInstance<RHMIEvent.FocusEvent>().firstOrNull()?.triggerEvent(mapOf(0 to listWidget.id))
				}
			}
		}

		notification.actions.forEachIndexed { i, action ->
			var button = buttons[2+i]
			button.setEnabled(true)
			button.setSelectable(true)
			if (action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
				// TODO Implement <input> view reply
				button.setEnabled(false)
			}
			button.getTooltipModel()?.asRaDataModel()?.value = action.title.toString()
			button.getImageModel()?.asImageIdModel()?.imageId = 158
			button.getAction()?.asRAAction()?.rhmiActionCallback = object : RHMIAction.RHMIActionCallback {
				override fun onActionEvent(args: Map<*, *>?) {
					controller.action(notification, action.title.toString())
				}
			}
		}

		val focusEvent = carApp.events.values.filterIsInstance<RHMIEvent.FocusEvent>().firstOrNull()
		focusEvent?.getTargetModel()?.asRaDataModel()?.value = clearButton.id.toString()
		focusEvent?.triggerEvent(mapOf(0 to clearButton.id))
	}

	open inner class PhoneNotificationListener {
		fun onNotification(sbn: CarNotification) {
			val appname = phoneAppResources.getAppName(sbn.packageName)
			val titleLabel = statePopup.getTextModel()?.asRaDataModel() ?: return
			val bodyLabel1 = statePopup.componentsList.filterIsInstance<RHMIComponent.Label>().firstOrNull()?.getModel()?.asRaDataModel() ?: return
			val bodyLabel2 = statePopup.componentsList.filterIsInstance<RHMIComponent.Label>().getOrNull(1)?.getModel()?.asRaDataModel() ?: return
			titleLabel.value = appname
			bodyLabel1.value = sbn.title.toString()
			bodyLabel2.value = sbn.text.toString()
			carApp.events.values.filterIsInstance<RHMIEvent.PopupEvent>().firstOrNull { it.getTarget() == statePopup }?.triggerEvent()
		}

		fun updateNotificationList() {
			DeferredUpdate.trigger("PhoneNotificationList", {
				val interactionTimeAgo = System.currentTimeMillis() - lastInteractionTime
				val interactionTimeRemaining = INTERACTION_DEBOUNCE_MS - interactionTimeAgo
				interactionTimeRemaining
			}, {
				if (listFocused) {
					this@PhoneNotifications.updateNotificationList()
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