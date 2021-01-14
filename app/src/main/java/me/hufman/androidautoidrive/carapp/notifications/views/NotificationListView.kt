package me.hufman.androidautoidrive.carapp.notifications.views

import android.os.Handler
import android.util.Log
import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.notifications.CarNotification
import me.hufman.androidautoidrive.carapp.notifications.NotificationSettings
import me.hufman.androidautoidrive.carapp.notifications.ReadoutInteractions
import me.hufman.androidautoidrive.notifications.NotificationsState
import me.hufman.androidautoidrive.carapp.notifications.TAG
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import me.hufman.idriveconnectionkit.rhmi.*
import java.util.*

class NotificationListView(val state: RHMIState, val graphicsHelpers: GraphicsHelpers, val settings: NotificationSettings, val readoutInteractions: ReadoutInteractions) {
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
	val notificationIconEvent: RHMIEvent.NotificationIconEvent    // to trigger the status bar icon

	var visible = false                 // whether the notification list is showing

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
	val notificationListData = object: RHMIListAdapter<CarNotification>(3, shownNotifications) {
		override fun convertRow(index: Int, item: CarNotification): Array<Any> {
			val icon = item.icon?.let { graphicsHelpers.compress(it, 48, 48) } ?: ""
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
				AppSettings.KEYS.NOTIFICATIONS_SOUND -> L.NOTIFICATION_SOUND
				AppSettings.KEYS.NOTIFICATIONS_READOUT -> L.NOTIFICATION_READOUT
				AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP -> L.NOTIFICATION_READOUT_POPUP
				AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER -> L.NOTIFICATION_READOUT_POPUP_PASSENGER
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
				val didEntryButton = timeSinceEntryButton < SKIPTHROUGH_THRESHOLD
				val focusEvent = state.app.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first()
				val skipThroughNotification = readoutInteractions.currentNotification ?:
						if (timeSinceNotificationArrival < ARRIVAL_THRESHOLD) mostInterestingNotification else null
				if (didEntryButton && skipThroughNotification != null) {
					detailsView.selectedNotification = skipThroughNotification
					// don't try to skip through to a new notification again
					notificationArrivalTimestamp = 0L
					try {
						focusEvent.triggerEvent(mapOf(0 to detailsView.state.id))   // skip through to details view
						// done processing here, don't continue on to redrawing
						return@FocusCallback
					} catch (e: BMWRemoting.ServiceException) {
						Log.w(TAG, "Failed to skip through to the speaking notification: $e")
					}
				} else {
					// we are backing out of a details view, cancel any current readout
					readoutInteractions.cancel()
				}
				// if we did not skip through, refresh:
				hideStatusBarIcon()
				redrawNotificationList()

				// if a notification is speaking, pre-select it
				// otherwise pre-select the most recent notification that showed up or was selected
				// and only if the user is freshly arriving, not backing out of a deeper view
				val preselectedNotification = readoutInteractions.currentNotification ?: mostInterestingNotification
				val index = shownNotifications.indexOf(preselectedNotification)
				if (didEntryButton && index >= 0) {
					focusEvent.triggerEvent(mapOf(0 to notificationListView.id, 41 to index))
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
				if (invokedBy != 2) {       // don't change the notification
					val notification = shownNotifications.getOrNull(index)
					detailsView.selectedNotification = notification
				}
				if (detailsView.selectedNotification != null) {
					// save this notification for future pre-selections
					mostInterestingNotification = detailsView.selectedNotification

					// set the list to go into the details state
					notificationListView.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = detailsView.state.id
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

			settingsListView.setVisible(true)
			settingsListView.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH.id, "55,0,*")
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
		settingsListView.getModel()?.value = menuSettingsListData
	}

	fun showNotification(sbn: CarNotification) {
		mostInterestingNotification = sbn
		notificationArrivalTimestamp = System.currentTimeMillis()
		showStatusBarIcon()
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