package me.hufman.androidautoidrive.carapp.notifications

import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIActionListCallback
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIEvent
import me.hufman.androidautoidrive.notifications.CarNotification

interface StatusbarController {
	fun add(sbn: CarNotification)
	fun retainAll(bounds: Collection<CarNotification>)
	fun remove(sbn: CarNotification)
	fun clear()
}

/** Allows for swapping out an underlying StatusbarController */
class StatusbarControllerWrapper(var controller: StatusbarController): StatusbarController {
	override fun add(sbn: CarNotification) {
		controller.add(sbn)
	}

	override fun retainAll(bounds: Collection<CarNotification>) {
		controller.retainAll(bounds)
	}

	override fun remove(sbn: CarNotification) {
		controller.remove(sbn)
	}

	override fun clear() {
		controller.clear()
	}
}

/** Tracks which CarNotifications are active for the Statusbar */
abstract class BaseStatusbarController: StatusbarController {
	protected val indices = HashMap<String, Int>()
	protected val notifications = HashMap<Int, CarNotification>()

	override fun add(sbn: CarNotification) {
		synchronized(indices) {
			val index = indices[sbn.key] ?: (indices.values.maxOrNull() ?: -1) + 1
			indices[sbn.key] = index
			notifications[index] = sbn
		}
	}
	override fun retainAll(bounds: Collection<CarNotification>) {
		synchronized(indices) {
			val boundsKeys = bounds.map { it.key }.toSet()
			indices.keys.toList().forEach { key ->
				if (!boundsKeys.contains(key)) {
					remove(key)
				}
			}
		}
	}
	override fun remove(sbn: CarNotification) {
		remove(sbn.key)
	}
	open fun remove(key: String) {
		synchronized(indices) {
			val index = indices.remove(key)
			notifications.remove(index)
		}
	}
	override fun clear() {
		synchronized(indices) {
			indices.clear()
			notifications.clear()
		}
	}
}

/**
 * Toggles the ID4 statusbar icon based on whether there's any new messages
 */
class ID4StatusbarController(val notificationIconEvent: RHMIEvent.NotificationIconEvent, val imageId: Int): BaseStatusbarController() {
	override fun add(sbn: CarNotification) {
		super.add(sbn)

		notificationIconEvent.getImageIdModel()?.asImageIdModel()?.imageId = imageId
		try {
			notificationIconEvent.triggerEvent(mapOf(0 to true))
		} catch (e: BMWRemoting.ServiceException) {
			// error showing icon
		}
	}

	override fun remove(key: String) {
		super.remove(key)

		// remove the icon if not showing any messages
		if (indices.isEmpty()) {
			clear()
		}
	}

	override fun clear() {
		super.clear()
		try {
			notificationIconEvent.triggerEvent(mapOf(0 to false))
		} catch (e: BMWRemoting.ServiceException) {
			// error showing icon
		}
	}
}

/**
 * Manages the messages shown in the Notification Center
 */
class ID5NotificationCenter(val notificationEvent: RHMIEvent.NotificationEvent, val imageId: Int): BaseStatusbarController(), RHMIActionListCallback {
	var onClicked: ((CarNotification) -> Unit)? = null
	override fun add(sbn: CarNotification) {
		synchronized(indices) {
			super.add(sbn)
			val index = indices[sbn.key] ?: 0

			try {
				notificationEvent.getIndexId()?.asRaIntModel()?.value = index
				notificationEvent.getTitleTextModel()?.asRaDataModel()?.value = sbn.title
				notificationEvent.getNotificationTextModel()?.asRaDataModel()?.value = sbn.lastLine
				notificationEvent.getImageModel()?.asImageIdModel()?.imageId = imageId
				notificationEvent.triggerEvent(mapOf(0.toByte() to true))
			} catch (e: BMWRemoting.ServiceException) {
				// error showing icon
				e.printStackTrace()
			}
		}
	}

	override fun remove(key: String) {
		synchronized(indices) {
			val index = indices[key]
			super.remove(key)
			if (index != null) {
				try {
					notificationEvent.getIndexId()?.asRaIntModel()?.value = index
					notificationEvent.triggerEvent(mapOf(0.toByte() to false))
				} catch (e: BMWRemoting.ServiceException) {
					// error showing icon
				}
			}
		}
	}

	override fun clear() {
		synchronized(indices) {
			// remove each of the messages
			indices.keys.toList().forEach {
				remove(it)
			}
		}
	}

	override fun onAction(index: Int, invokedBy: Int?) {
		// user clicked a message in the Notification Center
		val notification = notifications[index] ?: return
		onClicked?.invoke(notification)
	}
}