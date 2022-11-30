package me.hufman.androidautoidrive.carapp.notifications.views

import android.util.Log
import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.rhmi.*
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.carapp.FocusTriggerController
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.notifications.*
import me.hufman.androidautoidrive.notifications.CarNotification
import me.hufman.androidautoidrive.notifications.CarNotificationController
import me.hufman.androidautoidrive.notifications.NotificationsState
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import java.util.*
import kotlin.math.min

class DetailsView(val state: RHMIState, val phoneAppResources: PhoneAppResources, val graphicsHelpers: GraphicsHelpers,
                  val notificationSettings: NotificationSettings, val controller: CarNotificationController,
                  val focusTriggerController: FocusTriggerController, val statusbarController: StatusbarController, val readoutInteractions: ReadoutInteractions) {
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.ToolbarState &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().firstOrNull {
						it.getModel()?.modelType == "Richtext"
					} != null
		}

		const val MAX_LENGTH = 10000
	}

	var listState: RHMIState = state        // where to set the focus when the active notification disappears, linked during initWidgets
	val iconWidget: RHMIComponent.List     // the widget to display the notification app's icon
	val titleWidget: RHMIComponent.List    // the widget to display the title in
	val listWidget: RHMIComponent.List     // the widget to display the text
	val imageWidget: RHMIComponent.Image
	lateinit var inputView: RHMIState

	var visible = false
	var selectedNotification: CarNotification? = null

	init {
		iconWidget = state.componentsList.filterIsInstance<RHMIComponent.List>().first()
		titleWidget = state.componentsList.filterIsInstance<RHMIComponent.List>()[1]
		listWidget = state.componentsList.filterIsInstance<RHMIComponent.List>().first {
			it.getModel()?.modelType == "Richtext"
		}
		imageWidget = state.componentsList.filterIsInstance<RHMIComponent.Image>().first()
	}

	fun initWidgets(listView: NotificationListView, inputState: RHMIState) {
		state as RHMIState.ToolbarState
		this.inputView = inputState

		state.focusCallback = FocusCallback { focused ->
			visible = focused
			if (focused) {
				show()

				// read out
				val selectedNotification = selectedNotification
				if (selectedNotification != null) {
					readoutInteractions.triggerDisplayReadout(selectedNotification)
				}
			}
		}
		state.visibleCallback = VisibleCallback { visible ->
			if (!visible) {
				hide()
			}
		}

		state.setProperty(RHMIProperty.PropertyId.HMISTATE_TABLETYPE, 3)
		state.componentsList.forEach { it.setVisible(false) }
		// separator below the title
		state.componentsList.filterIsInstance<RHMIComponent.Separator>().forEach { it.setVisible(true) }
		iconWidget.apply {
			// app icon and notification title
			setVisible(true)
			setEnabled(true)
			setSelectable(true)
			setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH.id, "55,0,*")
		}
		titleWidget.apply {
			// the title and any side icon
			setVisible(true)
			setEnabled(true)
			setSelectable(true)
		}
		imageWidget.apply {
			setProperty(RHMIProperty.PropertyId.WIDTH.id, 400)
			setProperty(RHMIProperty.PropertyId.HEIGHT.id, 300)
		}
		listWidget.apply {
			// text
			setVisible(true)
			setEnabled(true)
			setSelectable(true)
		}
		state.toolbarComponentsList.forEach {
			if (it.getAction() != null) {
				it.setSelectable(false)
				it.setEnabled(false)
				it.setVisible(true)
			}
		}
		this.listState = listView.state
	}

	/**
	 * When we detect that the car is in Parked mode, lock the SpeedLock setting to stay unlocked
	 */
	fun lockSpeedLock() {
		state.setProperty(RHMIProperty.PropertyId.SPEEDLOCK, false)
	}

	fun hide() {
		val emptyList = RHMIModel.RaListModel.RHMIListConcrete(1)
		iconWidget.getModel()?.setValue(emptyList, 0, 0, 0)
		titleWidget.getModel()?.setValue(emptyList, 0, 0, 0)
		listWidget.getModel()?.setValue(emptyList, 0, 0, 0)
		imageWidget.setVisible(false)
	}

	fun show() {
		// set the focus to the first button
		state as RHMIState.ToolbarState
		val buttons = ArrayList(state.toolbarComponentsList).filterIsInstance<RHMIComponent.ToolbarButton>().filter { it.action > 0}
		focusTriggerController.focusComponent(buttons[0])

		redraw()

		// remove the message from the Notification Center, since we are reading it now
		selectedNotification?.also {
			statusbarController.remove(it)
		}
	}

	fun redraw() {
		state as RHMIState.ToolbarState

		if (!visible) {
			// not visible, skip the redraw
			return
		}

		// find the notification, or bail to the list
		val notification = NotificationsState.getNotificationByKey(selectedNotification?.key)
		if (notification == null) {
			focusTriggerController.focusState(listState, false)
			return
		}

		// prepare the app icon and title
		val appIcon = notification.appIcon?.let {graphicsHelpers.compress(it, 48, 48)} ?: ""
		val appName = notification.appName
		val iconListData = RHMIModel.RaListModel.RHMIListConcrete(3)
		iconListData.addRow(arrayOf(appIcon, "", appName))

		// prepare the title data
		var sidePictureWidth = 0
		val titleListData = RHMIModel.RaListModel.RHMIListConcrete(2)
		if (notification.sidePicture == null || notification.sidePicture.intrinsicHeight <= 0) {
			titleListData.addRow(arrayOf("", notification.title))
		} else {
			val sidePictureHeight = 96  // force the side picture to be this tall
			sidePictureWidth = (sidePictureHeight.toFloat() / notification.sidePicture.intrinsicHeight * notification.sidePicture.intrinsicWidth).toInt()
			val sidePicture = graphicsHelpers.compress(notification.sidePicture, sidePictureWidth, sidePictureHeight)
			titleListData.addRow(arrayOf(BMWRemoting.RHMIResourceData(BMWRemoting.RHMIResourceType.IMAGEDATA, sidePicture), notification.title + "\n"))
		}
		titleWidget.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH.id, "$sidePictureWidth,*")

		// prepare the notification text
		val listData = RHMIModel.RaListModel.RHMIListConcrete(1)
		val trimmedText = notification.text.substring(0, min(MAX_LENGTH, notification.text.length))
		val sanitizedText = trimmedText.replace("<", "<\u200b")     // break up any incidental <info or <color= sequences with a zero-width-space
		listData.addRow(arrayOf(sanitizedText))

		state.getTextModel()?.asRaDataModel()?.value = appName
		iconWidget.getModel()?.value = iconListData
		titleWidget.getModel()?.value = titleListData
		listWidget.getModel()?.value = listData

		// try to load a picture from the notification
		var pictureWidth = 400
		var pictureHeight = 300
		val pictureDrawable = try {
			notification.picture ?: notification.pictureUri?.let { phoneAppResources.getUriDrawable(it) }
		} catch (e: Exception) {
			Log.w(TAG, "Failed to open picture from ${notification.pictureUri}", e)
			null
		}
		val picture = if (pictureDrawable != null && pictureDrawable.intrinsicHeight > 0) {
			pictureHeight = min(300, pictureDrawable.intrinsicHeight)
			pictureWidth = (pictureHeight.toFloat() / pictureDrawable.intrinsicHeight * pictureDrawable.intrinsicWidth).toInt()
			graphicsHelpers.compress(pictureDrawable, pictureWidth, pictureHeight, quality = 65)
		} else { null }
		// if we have a picture to display
		if (picture != null) {
			// set the dimensions, ID4 clips images to this rectangle
			imageWidget.setProperty(RHMIProperty.PropertyId.HEIGHT, pictureHeight)
			imageWidget.setProperty(RHMIProperty.PropertyId.WIDTH, pictureWidth)
			imageWidget.setVisible(true)
			imageWidget.getModel()?.asRaImageModel()?.value = picture
		} else {
			imageWidget.setVisible(false)
		}

		// find and enable the clear button
		val buttons = ArrayList(state.toolbarComponentsList).filterIsInstance<RHMIComponent.ToolbarButton>().filter { it.action > 0}
		var buttonIndex = 0
		if (notification.isClearable) {
			buttons[buttonIndex].apply {
				setEnabled(true)
				setSelectable(true)
				getImageModel()?.asImageIdModel()?.imageId = 150
				getTooltipModel()?.asRaDataModel()?.value = L.NOTIFICATION_CLEAR_ACTION
				getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
					controller.clear(notification.key)
					getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = listState.id
				}
			}
			buttonIndex++
		}
		// add a readout-action in case automatic readout is not enabled
		if (!notificationSettings.shouldReadoutNotificationDetails()) {
			buttons[buttonIndex].apply {
				setEnabled(true)
				setSelectable(true)
				getImageModel()?.asImageIdModel()?.imageId = 154
				getTooltipModel()?.asRaDataModel()?.value = L.NOTIFICATION_READOUT_ACTION
				getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
					readoutInteractions.triggerDisplayReadout(notification, ignoreSetting = true)
					getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = state.id
				}
			}
			buttonIndex++
		}
		// enable any custom actions
		buttons.subList(buttonIndex,6).forEachIndexed { index, button ->
			button.apply {
				notification.actions.getOrNull(index)?.also { action ->
					setEnabled(true)
					setSelectable(true)
					getImageModel()?.asImageIdModel()?.imageId = 158
					getTooltipModel()?.asRaDataModel()?.value = UnicodeCleaner.clean(action.name.toString())
					getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
						if (action.supportsReply) {
							// show input to reply
							getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = inputView.id
							val replyController = ReplyControllerNotification(notification, action, controller, notificationSettings.quickReplies)
							ReplyView(listState, inputView, replyController)
							readoutInteractions.cancel()
						} else {
							// trigger the custom action
							controller.action(notification.key, action.name.toString())
							getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = listState.id
						}
					}
				} ?: run {
					setEnabled(false)
					setSelectable(false)
					getAction()?.asRAAction()?.rhmiActionCallback = null // don't leak memory
				}
			}
		}
	}
}