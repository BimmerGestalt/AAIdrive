package me.hufman.androidautoidrive.carapp.notifications

import java.util.*
import kotlin.collections.LinkedHashMap

object NotificationsState {
	private const val POPPED_HISTORY_SIZE = 15
	val notifications = ArrayList<CarNotification>()    // the notifications shown in the list
	val poppedNotifications = Collections.newSetFromMap(object: LinkedHashMap<CarNotification, Boolean>(POPPED_HISTORY_SIZE) {
		override fun removeEldestEntry(_eldest: MutableMap.MutableEntry<CarNotification, Boolean>?): Boolean {
			return size > POPPED_HISTORY_SIZE
		}
	})
	var selectedNotification: CarNotification? = null

	fun getNotificationByKey(key: String?): CarNotification? {
		key ?: return null
		return synchronized(notifications) {
			notifications.find { it.key == key }
		}
	}

	/**
	 * Return the selected notification, but only if it is still active
	 */
	fun fetchSelectedNotification(): CarNotification? {
		return synchronized(notifications) {
			val key = selectedNotification?.key
			if (key != null) {
				val currentNotification = getNotificationByKey(key)
				selectedNotification = currentNotification
			}
			selectedNotification
		}

	}
}