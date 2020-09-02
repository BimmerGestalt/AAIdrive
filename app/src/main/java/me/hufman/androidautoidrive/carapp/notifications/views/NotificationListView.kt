package me.hufman.androidautoidrive.carapp.notifications.views

import android.os.Handler
import android.util.Log
import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.carapp.notifications.CarNotification
import me.hufman.androidautoidrive.carapp.notifications.NotificationSettings
import me.hufman.androidautoidrive.carapp.notifications.NotificationsState
import me.hufman.androidautoidrive.carapp.notifications.TAG
import me.hufman.idriveconnectionkit.rhmi.*
import java.util.*

class NotificationListView(val state: RHMIState, val phoneAppResources: PhoneAppResources, val graphicsHelpers: GraphicsHelpers, val settings: NotificationSettings) {
	companion object {
		val INTERACTION_DEBOUNCE_MS = 2000              // how long to wait after lastInteractionTime to update the list

		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().size >= 2 &&
					state.componentsList.indexOfLast { it is RHMIComponent.Label } < state.componentsList.indexOfLast { it is RHMIComponent.List }
		}
		const val IMAGEID_CHECKMARK = 150
	}

	val notificationListView: RHMIComponent.List    // the list component of notifications
	val settingsListView: RHMIComponent.List    // the list component of notifications
	val notificationIconEvent: RHMIEvent.NotificationIconEvent    // to trigger the status bar icon

	var visible = false                 // whether the notification list is showing

	var deferredUpdate: DeferredUpdate? = null  // wrapper object to help debounce user inputs
	var lastInteractionIndex: Int = -1       // what index the user last selected

	val shownNotifications = Collections.synchronizedList(ArrayList<CarNotification>())   // which notifications are showing
	val notificationListData = object: RHMIListAdapter<CarNotification>(3, shownNotifications) {
		override fun convertRow(index: Int, item: CarNotification): Array<Any> {
			val icon = graphicsHelpers.compress(phoneAppResources.getIconDrawable(item.icon), 48, 48)
			val text = "${item.title}\n${item.text.trim().split(Regex("\n")).lastOrNull() ?: ""}"
			return arrayOf(icon, "", text)
		}
	}
	val emptyListData = RHMIModel.RaListModel.RHMIListConcrete(3).apply {
		addRow(arrayOf("", "", L.NOTIFICATIONS_EMPTY_LIST))
	}

	val menuSettingsListData = object: RHMIListAdapter<AppSettings.KEYS>(3, settings.getSettings()) {
		override fun convertRow(index: Int, item: AppSettings.KEYS): Array<Any> {
			val checked = settings.isChecked(item)
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
		notificationIconEvent = state.app.events.values.filterIsInstance<RHMIEvent.NotificationIconEvent>().first()
	}

	fun initWidgets(detailsView: DetailsView) {
		// refresh the list when we are displayed
		state.focusCallback = FocusCallback { focused ->
			visible = focused
			if (focused) {
				hideStatusBarIcon()
				redrawNotificationList()
				settings.callback = {
					redrawNotificationList()
				}
			} else {
				settings.callback = null
			}
		}

		state.getTextModel()?.asRaDataModel()?.value = L.NOTIFICATIONS_TITLE
		state.setProperty(24, 3)
		state.componentsList.forEach { it.setVisible(false) }

		notificationListView.setVisible(true)
		notificationListView.setProperty(6, "55,0,*")
		notificationListView.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index ->
			val notification = shownNotifications.getOrNull(index)
			detailsView.selectedNotification = notification
			if (notification != null) {
				// set the list to go into the details state
				notificationListView.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = detailsView.state.id
			} else {
				notificationListView.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = 0
			}
		}
		notificationListView.getSelectAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback {
			if (it != lastInteractionIndex) {
				lastInteractionIndex = it
				deferredUpdate?.defer(INTERACTION_DEBOUNCE_MS.toLong())
			}
		}

		if (settings.getSettings().isNotEmpty()) {
			state.componentsList.filterIsInstance<RHMIComponent.Label>().lastOrNull()?.let {
				it.getModel()?.asRaDataModel()?.value = L.NOTIFICATION_OPTIONS
				it.setVisible(true)
				it.setEnabled(false)
				it.setSelectable(false)
			}

			settingsListView.setVisible(true)
			settingsListView.setProperty(6, "55,0,*")
			settingsListView.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index ->
				val setting = menuSettingsListData.realData.getOrNull(index)
				if (setting != null) {
					settings.toggleSetting(setting)
				}
				throw RHMIActionAbort()
			}
		}

		notificationIconEvent.getImageIdModel()?.asImageIdModel()?.imageId = 157
	}

	fun onCreate(handler: Handler) {
		deferredUpdate = DeferredUpdate(handler)
	}

	/** Only redraw if the user hasn't clicked it recently
	 *  Gets called whenever the notification list changes
	 */
	fun gentlyUpdateNotificationList() {
		if (NotificationsState.cloneNotifications().isEmpty()) {
			hideStatusBarIcon()
		}

		if (!visible) {
			return
		}

		val deferredUpdate = this.deferredUpdate
		if (deferredUpdate == null) {
			Log.w(TAG, "DeferredUpdate not built yet, redrawing immediately")
			redrawNotificationList()
		} else {
			deferredUpdate.trigger {
				if (visible) {
					Log.i(TAG, "Updating list of notifications")
					redrawNotificationList()
				} else {
					Log.i(TAG, "Notification list is not on screen, skipping update")
				}
			}
		}
	}

	// should only be run from the DeferredUpdate thread, once at a time, but synchronize just in case
	fun redrawNotificationList() = synchronized(shownNotifications) {
		shownNotifications.clear()
		shownNotifications.addAll(NotificationsState.cloneNotifications())

		if (shownNotifications.isEmpty()) {
			notificationListView.getModel()?.value = emptyListData
		} else {
			notificationListView.getModel()?.value = notificationListData
		}
		settingsListView.getModel()?.value = menuSettingsListData
	}

	fun showStatusBarIcon() {
		// a new message arrived but the window is not visible, show the icon
		if (!visible) {
			try {
				notificationIconEvent.triggerEvent(mapOf(0 to true))
			} catch (e: BMWRemoting.ServiceException) {
				// error showing icon
			}
		}
	}
	fun hideStatusBarIcon() {
		try {
			notificationIconEvent.triggerEvent(mapOf(0 to false))
		} catch (e: BMWRemoting.ServiceException) {
			// error hiding icon
		}
	}

}