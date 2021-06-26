package me.hufman.androidautoidrive.carapp.notifications

import androidx.annotation.VisibleForTesting
import me.hufman.androidautoidrive.notifications.CarNotification
import java.util.*
import kotlin.collections.LinkedHashMap

class PopupHistory(val maxSize: Int = POPPED_HISTORY_SIZE) {
	companion object {
		private const val POPPED_HISTORY_SIZE = 15
	}

	@VisibleForTesting
	val poppedNotifications = Collections.newSetFromMap(object: LinkedHashMap<CarNotification, Boolean>(maxSize) {
		override fun removeEldestEntry(_eldest: MutableMap.MutableEntry<CarNotification, Boolean>?): Boolean {
			return size > maxSize
		}
	})

	fun contains(notification: CarNotification): Boolean = synchronized(poppedNotifications) {
		// Google Maps notifications change to a higher priority when they want to peek
		// so we forget when a popup has been seen if the priority has been bumped
		val previous = poppedNotifications.find { it == notification }
		val bumpedImportance = (notification.importance ?: 0) > (previous?.importance ?: 0)
		previous != null && !bumpedImportance
	}

	fun add(notification: CarNotification) = synchronized(poppedNotifications) {
		poppedNotifications.add(notification)
	}

	fun retainAll(bounds: Iterable<CarNotification>) = synchronized(poppedNotifications) {
		poppedNotifications.retainAll(bounds)
	}
}