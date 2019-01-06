package me.hufman.androidautoidrive.carapp.notifications

import android.os.Bundle

object NotificationsState {
	val notifications = ArrayList<CarNotification>()    // the notifications shown in the list
	var selectedNotification: CarNotification? = null

	fun getNotificationByKey(key: String): CarNotification? {
		return notifications.find { it.key == key }
	}
}