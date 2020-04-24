package me.hufman.androidautoidrive.carapp.notifications.views

import android.os.Handler
import android.util.Log
import me.hufman.androidautoidrive.DeferredUpdate
import me.hufman.androidautoidrive.GraphicsHelpers
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.carapp.notifications.CarNotification
import me.hufman.androidautoidrive.carapp.notifications.NotificationsState
import me.hufman.androidautoidrive.carapp.notifications.TAG
import me.hufman.idriveconnectionkit.rhmi.*
import java.util.*

class NotificationListView(val state: RHMIState, val phoneAppResources: PhoneAppResources, val graphicsHelpers: GraphicsHelpers) {
	companion object {
		val INTERACTION_DEBOUNCE_MS = 2000              // how long to wait after lastInteractionTime to update the list

		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty()
		}
	}

	val notificationListView: RHMIComponent.List    // the list component of notifications

	var visible = false                 // whether the notification list is showing

	var deferredUpdate: DeferredUpdate? = null  // wrapper object to help debounce user inputs
	var lastInteractionIndex: Int = -1       // what index the user last selected

	val shownNotifications = Collections.synchronizedList(ArrayList<CarNotification>())   // which notifications are showing
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

	init {
		notificationListView = state.componentsList.filterIsInstance<RHMIComponent.List>().first()
	}

	fun initWidgets(detailsView: DetailsView) {
		// refresh the list when we are displayed
		state.focusCallback = FocusCallback { focused ->
			visible = true
			if (focused) {
				redrawNotificationList()
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
	}

	fun onCreate(handler: Handler) {
		deferredUpdate = DeferredUpdate(handler)
	}

	/** Only redraw if the user hasn't clicked it recently */
	fun gentlyUpdateNotificationList() {
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
	}
}