package me.hufman.androidautoidrive.carapp.notifications

import androidx.annotation.VisibleForTesting
import me.hufman.androidautoidrive.notifications.CarNotification
import java.util.*

class PopupHistory(val maxSize: Int = POPPED_HISTORY_SIZE) {
	companion object {
		private const val POPPED_HISTORY_SIZE = 15
	}

	@VisibleForTesting
	val poppedNotifications: MutableSet<CarNotification> = Collections.newSetFromMap(object: LinkedHashMap<CarNotification, Boolean>(maxSize) {
		override fun removeEldestEntry(_eldest: MutableMap.MutableEntry<CarNotification, Boolean>?): Boolean {
			return size > maxSize
		}
	})

	fun contains(notification: CarNotification): Boolean = synchronized(poppedNotifications) {
		poppedNotifications.contains(notification)
	}

	fun add(notification: CarNotification) = synchronized(poppedNotifications) {
		poppedNotifications.add(notification)
	}

	fun retainAll(bounds: Iterable<CarNotification>) = synchronized(poppedNotifications) {
		poppedNotifications.retainAll(bounds)
	}
}