package me.hufman.androidautoidrive.carapp.notifications.views

import android.util.Log
import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.carapp.notifications.CarNotification
import me.hufman.androidautoidrive.carapp.notifications.NotificationsState
import me.hufman.androidautoidrive.carapp.notifications.TAG
import me.hufman.idriveconnectionkit.rhmi.*
import java.util.*

class NotificationListView(val state: RHMIState, val phoneAppResources: PhoneAppResources, val graphicsHelpers: GraphicsHelpers, val appSettings: MutableAppSettings) {
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().size >= 2 &&
					state.componentsList.indexOfLast { it is RHMIComponent.Label } < state.componentsList.indexOfLast { it is RHMIComponent.List }
		}
		const val IMAGEID_CHECKMARK = 150
	}

	val notificationListView: RHMIComponent.List    // the list component of notifications
	val settingsListView: RHMIComponent.List    // the list component of notifications

	var visible = false                 // whether the notification list is showing
	val INTERACTION_DEBOUNCE_MS = 2000              // how long to wait after lastInteractionTime to update the list
	var lastInteractionTime: Long = 0             // timestamp when the user last navigated in the main list
	var lastInteractionIndex: Int = -1       // what index the user last selected

	val shownNotifications = ArrayList<CarNotification>()   // which notifications are showing
	val notificationListData = object: RHMIListAdapter<CarNotification>(3, shownNotifications) {
		override fun convertRow(index: Int, item: CarNotification): Array<Any> {
			val icon = graphicsHelpers.compress(phoneAppResources.getIconDrawable(item.icon), 48, 48)
			val text = if (item.summary != null) "${item.title}\n${item.summary}" else "${item.title}\n${item.text}"
			return arrayOf(icon, "", text)
		}
	}
	val emptyListData = RHMIModel.RaListModel.RHMIListConcrete(3).apply {
		addRow(arrayOf("", "", L.NOTIFICATIONS_EMPTY_LIST))
	}

	val menuSettings = listOf(
			AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP,
			AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER
	)
	val menuSettingsListData = object: RHMIListAdapter<AppSettings.KEYS>(3, menuSettings) {
		override fun convertRow(index: Int, item: AppSettings.KEYS): Array<Any> {
			val checked = appSettings[item].toBoolean()
			val checkmark = if (checked) BMWRemoting.RHMIResourceIdentifier(BMWRemoting.RHMIResourceType.IMAGEID, IMAGEID_CHECKMARK) else ""
			val name = when (item) {
				AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP -> L.NOTIFICATION_POPUPS
				AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER -> L.NOTIFICATION_POPUPS_PASSENGER
				else -> ""
			}
			return arrayOf(checkmark, "", name)
		}
	}

	init {
		notificationListView = state.componentsList.filterIsInstance<RHMIComponent.List>().first()
		settingsListView = state.componentsList.filterIsInstance<RHMIComponent.List>().last()
	}

	fun initWidgets(detailsView: DetailsView) {
		// refresh the list when we are displayed
		state.focusCallback = FocusCallback { focused ->
			visible = true
			if (focused) {
				redrawNotificationList()
				appSettings.callback = {
					redrawNotificationList()
				}
			} else {
				appSettings.callback = null
			}
		}

		state.getTextModel()?.asRaDataModel()?.value = L.NOTIFICATIONS_TITLE
		state.setProperty(24, 3)
		state.componentsList.forEach { it.setVisible(false) }

		notificationListView.setVisible(true)
		notificationListView.setProperty(6, "55,0,*")
		notificationListView.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index ->
			val notification = shownNotifications.getOrNull(index)
			if (notification != null) {
				// set the list to go into the details state
				notificationListView.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = detailsView.state.id
				NotificationsState.selectedNotification = notification
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

		state.componentsList.filterIsInstance<RHMIComponent.Label>().lastOrNull()?.let {
			it.getModel()?.asRaDataModel()?.value = L.NOTIFICATION_OPTIONS
			it.setVisible(true)
			it.setEnabled(false)
			it.setSelectable(false)
		}

		settingsListView.setVisible(true)
		settingsListView.setProperty(6, "55,0,*")
		settingsListView.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index ->
			val option = menuSettings.getOrNull(index)
			if (option != null) {
				appSettings[option] = (!appSettings[option].toBoolean()).toString()
			}
			throw RHMIActionAbort()
		}
	}

	/** Only redraw if the user hasn't clicked it recently */
	fun gentlyUpdateNotificationList() {
		if (!visible) {
			return
		}

		DeferredUpdate.trigger("PhoneNotificationList", {
			val interactionTimeAgo = System.currentTimeMillis() - lastInteractionTime
			val interactionTimeRemaining = INTERACTION_DEBOUNCE_MS - interactionTimeAgo
			interactionTimeRemaining
		}, {
			if (visible) {
				Log.i(TAG, "Updating list of notifications")
				redrawNotificationList()
			} else {
				Log.i(TAG, "Notification list is not on screen, skipping update")
			}
		})
	}

	fun redrawNotificationList() {
		synchronized(NotificationsState.notifications) {
			shownNotifications.clear()
			shownNotifications.addAll(NotificationsState.notifications)
		}

		if (NotificationsState.notifications.isEmpty()) {
			notificationListView.getModel()?.value = emptyListData
		} else {
			notificationListView.getModel()?.value = notificationListData
		}
		settingsListView.getModel()?.value = menuSettingsListData
	}
}