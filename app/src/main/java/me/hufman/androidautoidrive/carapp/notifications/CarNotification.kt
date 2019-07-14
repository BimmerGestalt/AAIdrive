package me.hufman.androidautoidrive.carapp.notifications

import android.app.Notification
import android.graphics.drawable.Icon

class CarNotification(val packageName: String, val key: String, val icon: Icon, val isClearable: Boolean, val actions: Array<Notification.Action>,
                      var title: String?, var summary: String?, var text: String?) {
	override fun toString(): String {
		return "CarNotification(key='$key', title=$title)"
	}
}