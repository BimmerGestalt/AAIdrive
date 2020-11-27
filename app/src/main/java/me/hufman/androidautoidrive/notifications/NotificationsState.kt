package me.hufman.androidautoidrive.notifications

import androidx.annotation.VisibleForTesting
import java.util.*

object NotificationsState {
	@VisibleForTesting
	val notifications = ArrayList<CarNotification>()    // the notifications shown in the list

	fun getNotificationByKey(key: String?): CarNotification? {
		key ?: return null
		return synchronized(notifications) {
			notifications.find { it.key == key }
		}
	}

	fun cloneNotifications(): List<CarNotification> {
		return synchronized(notifications) {
			ArrayList(notifications)
		}
	}

	fun replaceNotifications(updated: Iterable<CarNotification>) {
		synchronized(notifications) {
			notifications.clear()
			notifications.addAll(updated)
		}
	}
}