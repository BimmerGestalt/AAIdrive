package me.hufman.androidautoidrive.carapp.notifications.views

import android.os.Handler
import android.util.Log
import io.bimmergestalt.idriveconnectkit.rhmi.*
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.FocusTriggerController
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.SettingsToggleList
import me.hufman.androidautoidrive.carapp.notifications.*
import me.hufman.androidautoidrive.carapp.notifications.TAG
import me.hufman.androidautoidrive.notifications.CarNotification
import me.hufman.androidautoidrive.notifications.NotificationsState
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import java.util.*

class NotificationListView(val state: RHMIState, val graphicsHelpers: GraphicsHelpers, val settings: NotificationSettings,
                           val focusTriggerController: FocusTriggerController, val statusbarController: StatusbarController, val readoutInteractions: ReadoutInteractions) {
	companion object {
		const val INTERACTION_DEBOUNCE_MS = 2000              // how long to wait after lastInteractionTime to update the list
		const val SKIPTHROUGH_THRESHOLD = 2000                // how long after an entrybutton push to allow skipping through to a current notification
		const val ARRIVAL_THRESHOLD = 8000                    // how long after a new notification should it skip through

		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().size >= 2 &&
					state.componentsList.indexOfLast { it is RHMIComponent.Label } < state.componentsList.indexOfLast { it is RHMIComponent.List }
		}
		const val IMAGEID_CHECKMARK = 150
	}

	val notificationListView: RHMIComponent.List    // the list component of notifications
	val settingsListView: RHMIComponent.List    // the list component of notifications
	val settingsView: SettingsToggleList    // the list of settings to be toggled

	var visible = false                 // whether the notification list is showing
	var firstView = true                // whether this is the first time this view is shown

	var entryButtonTimestamp = 0L   // when the user pushed the entryButton
	val timeSinceEntryButton: Long
		get() = System.currentTimeMillis() - entryButtonTimestamp

	var deferredUpdate: DeferredUpdate? = null  // wrapper object to help debounce user inputs
	var lastInteractionIndex: Int = -1       // what index the user last selected

	var notificationArrivalTimestamp = 0L
	val timeSinceNotificationArrival: Long
		get() = System.currentTimeMillis() - notificationArrivalTimestamp
	var mostInterestingNotification: CarNotification? = null        // most recently arrived or selected notification

	val shownNotifications = Collections.synchronizedList(ArrayList<CarNotification>())   // which notifications are showing
	val notificationListData = object: RHMIModel.RaListModel.RHMIListAdapter<CarNotification>(3, shownNotifications) {
		override fun convertRow(index: Int, item: CarNotification): Array<Any> {
			val icon = item.icon?.let { graphicsHelpers.compress(it, 48, 48) } ?: ""
			val text = "${item.title}\n${item.text.trim().split(Regex("\n")).lastOrNull() ?: ""}"
			return arrayOf(icon, "", text)
		}
	}
	val emptyListData = RHMIModel.RaListModel.RHMIListConcrete(3).apply {
		addRow(arrayOf("", "", L.NOTIFICATIONS_EMPTY_LIST))
	}

	init {
		notificationListView = state.componentsList.filterIsInstance<RHMIComponent.List>().first()
		settingsListView = state.componentsList.filterIsInstance<RHMIComponent.List>().last()
		settingsView = SettingsToggleList(settingsListView, settings.appSettings, settings.getSettings(), IMAGEID_CHECKMARK)
	}

	fun initWidgets(showNotificationController: ShowNotificationController, permissionView: PermissionView) {
		// refresh the list when we are displayed
		state.focusCallback = FocusCallback { focused ->
			visible = focused
			if (firstView && !NotificationsState.serviceConnected) {
				focusTriggerController.focusState(permissionView.state, false)   // skip through to permissions view
				firstView = false
			} else if (focused) {
				val didEntryButton = timeSinceEntryButton < SKIPTHROUGH_THRESHOLD
				val skipThroughNotification = readoutInteractions.currentNotification ?:
						if (timeSinceNotificationArrival < ARRIVAL_THRESHOLD) mostInterestingNotification else null
				if (didEntryButton && skipThroughNotification != null) {
					// don't try to skip through to a new notification again
					notificationArrivalTimestamp = 0L
					val success = showNotificationController.showFromFocusEvent(skipThroughNotification, false)
					// done processing here, don't continue on to redrawing
					if (success) {
						return@FocusCallback
					}
				} else {
					// we are backing out of a details view, cancel any current readout
					readoutInteractions.cancel()
				}
				// if we did not skip through, refresh:
				redrawNotificationList()

				// viewing the entire list, so clear the statusbar icon
				hideStatusBarIcon()

				// if a notification is speaking, pre-select it
				// otherwise pre-select the most recent notification that showed up or was selected
				// and only if the user is freshly arriving, not backing out of a deeper view
				val preselectedNotification = readoutInteractions.currentNotification ?: mostInterestingNotification
				val index = shownNotifications.indexOf(preselectedNotification)
				if (didEntryButton && index >= 0) {
					focusTriggerController.focusComponent(notificationListView, index)
				}

				redrawSettingsList()
				settings.callback = {
					redrawSettingsList()
				}
			} else {
				settings.callback = null
			}
		}

		state.getTextModel()?.asRaDataModel()?.value = L.NOTIFICATIONS_TITLE
		state.setProperty(RHMIProperty.PropertyId.HMISTATE_TABLETYPE, 3)
		state.componentsList.forEach { it.setVisible(false) }

		notificationListView.setVisible(true)
		notificationListView.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH.id, "55,0,*")
		notificationListView.setProperty(RHMIProperty.PropertyId.BOOKMARKABLE, true)
		notificationListView.getAction()?.asRAAction()?.rhmiActionCallback = object: RHMIActionListCallback {
			override fun onAction(index: Int, invokedBy: Int?) {
				val notification = if (invokedBy != 2) {       // don't change the notification
					shownNotifications.getOrNull(index)
				} else {
					showNotificationController.getSelectedNotification()
				}
				if (notification != null) {
					// save this notification for future pre-selections
					mostInterestingNotification = notification

					// set the list to go into the details state
					showNotificationController.showFromHmiAction(notificationListView.getAction()?.asHMIAction(), notification)
				} else {
					notificationListView.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = 0
				}
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

			settingsView.initWidgets()
		}
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
			deferredUpdate.trigger(0) {
				if (visible) {
					Log.i(TAG, "Updating list of notifications")
					redrawNotificationList()
				} else {
					Log.i(TAG, "Notification list is not on screen, skipping update")
				}
				deferredUpdate.defer(INTERACTION_DEBOUNCE_MS.toLong())   // wait at least this long before doing another update
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
	}

	fun redrawSettingsList() {
		settingsView.redraw()
	}

	fun showNotification(sbn: CarNotification) {
		mostInterestingNotification = sbn
		notificationArrivalTimestamp = System.currentTimeMillis()
		// a new message arrived but the window is not visible, show the icon
		if (!visible) {
			statusbarController.add(sbn)
		}
	}

	fun hideStatusBarIcon() {
		statusbarController.clear()
	}
}