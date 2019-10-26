package me.hufman.androidautoidrive.carapp.notifications.views

import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.carapp.notifications.CarNotificationController
import me.hufman.androidautoidrive.carapp.notifications.NotificationsState
import me.hufman.idriveconnectionkit.rhmi.*
import java.util.ArrayList

class DetailsView(val state: RHMIState, val phoneAppResources: PhoneAppResources, val controller: CarNotificationController) {
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.ToolbarState &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().firstOrNull {
						it.getModel()?.modelType == "Richtext"
					} != null
		}

		const val MAX_LENGTH = 500
	}

	var listViewId: Int = 0                // where to set the focus when the active notification disappears
	val iconWidget: RHMIComponent.List     // the widget to display the notification app's icon
	val listWidget: RHMIComponent.List     // the widget to display the text

	var visible = false
	init {
		iconWidget = state.componentsList.filterIsInstance<RHMIComponent.List>().first()
		listWidget = state.componentsList.filterIsInstance<RHMIComponent.List>().first {
			it.getModel()?.modelType == "Richtext"
		}
	}

	fun initWidgets(listView: NotificationListView) {
		state as RHMIState.ToolbarState

		state.focusCallback = FocusCallback { focused ->
			visible = focused
			if (focused) {
				redraw()
			}
		}

		state.setProperty(24, 3)
		state.setProperty(36, false)
		state.componentsList.forEach { it.setVisible(false) }
		state.componentsList.filterIsInstance<RHMIComponent.List>().firstOrNull()?.apply {
			// app icon and notification title
			setVisible(true)
			setEnabled(true)
			setSelectable(true)
			setProperty(6, "55,0,*")
		}
		state.componentsList.filterIsInstance<RHMIComponent.List>().firstOrNull {
			it.getModel()?.modelType == "Richtext"
		}?.apply {
			// text
			setVisible(true)
			setEnabled(true)
		}

		var buttons = ArrayList(state.toolbarComponentsList).filterIsInstance<RHMIComponent.ToolbarButton>().filter { it.action > 0}
		state.toolbarComponentsList.forEach {
			if (it.getAction() != null) {
				it.setSelectable(false)
				it.setEnabled(false)
				it.setVisible(true)
			}
		}
		buttons[0].getImageModel()?.asImageIdModel()?.imageId = 150
		buttons[0].setVisible(true)
		buttons[0].setSelectable(true)
		buttons.subList(1, 6).forEach {
			it.getImageModel()?.asImageIdModel()?.imageId = 158
		}
		buttons.forEach {
			// go back to the main list when an action is clicked
			it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = listView.state.id
		}

		listViewId = listView.state.id
	}

	fun show() {
		redraw()

		// set the focus to the first button
		state as RHMIState.ToolbarState
		var buttons = ArrayList(state.toolbarComponentsList).filterIsInstance<RHMIComponent.ToolbarButton>().filter { it.action > 0}
		state.app.events.values.filterIsInstance<RHMIEvent.FocusEvent>().firstOrNull()?.triggerEvent(mapOf(0 to buttons[0].id))
	}

	fun redraw() {
		state as RHMIState.ToolbarState

		if (!visible) {
			// not visible, skip the redraw
			return
		}

		// find the notification, or bail to the list
		val notification = NotificationsState.fetchSelectedNotification()
		if (notification == null) {
			state.app.events.values.filterIsInstance<RHMIEvent.FocusEvent>().firstOrNull()?.triggerEvent(mapOf(0 to listViewId))
			return
		}

		// prepare the app icon and title
		val icon = phoneAppResources.getBitmap(phoneAppResources.getIconDrawable(notification.icon), 48, 48)
		val appname = phoneAppResources.getAppName(notification.packageName)
		val iconListData = RHMIModel.RaListModel.RHMIListConcrete(3)
		iconListData.addRow(arrayOf(icon, "", appname))

		// prepare the notification text
		val listData = RHMIModel.RaListModel.RHMIListConcrete(1)
		listData.addRow(arrayOf("${notification.title}\n${notification.text}"))

		state.getTextModel()?.asRaDataModel()?.value = notification.title ?: appname
		iconWidget.getModel()?.value = iconListData
		listWidget.getModel()?.value = listData

		// find and enable the clear button
		var buttons = ArrayList(state.toolbarComponentsList).filterIsInstance<RHMIComponent.ToolbarButton>().filter { it.action > 0}
		val clearButton = buttons[0]
		if (notification.isClearable) {
			clearButton.setEnabled(true)
			clearButton.getTooltipModel()?.asRaDataModel()?.value = L.NOTIFICATION_CLEAR_ACTION
			clearButton.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
				controller.clear(notification)
				Thread.sleep(50)
			}
		} else {
			clearButton.setEnabled(false)
		}

		// enable any custom actions
		(0..4).forEach {i ->
			val action = notification.actions.getOrNull(i)
			var button = buttons[1+i]
			if (action == null) {
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
				button.getTooltipModel()?.asRaDataModel()?.value = action.title.toString()
				button.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
					controller.action(notification, action.title.toString())
				}
			}
		}
	}
}