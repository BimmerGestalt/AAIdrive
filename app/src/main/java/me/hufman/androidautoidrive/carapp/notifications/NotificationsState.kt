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

	fun getNotificationByKey(key: String): CarNotification? {
		return notifications.find { it.key == key }
	}
}