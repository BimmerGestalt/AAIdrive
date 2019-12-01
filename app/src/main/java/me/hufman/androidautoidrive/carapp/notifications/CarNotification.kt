package me.hufman.androidautoidrive.carapp.notifications

import android.app.Notification
import android.graphics.drawable.Icon

class CarNotification(val packageName: String, val key: String, val icon: Icon, val isClearable: Boolean, val actions: Array<Notification.Action>,
                      val title: String?, val summary: String?, val text: String?) {
	override fun toString(): String {
		return "CarNotification(key='$key', title=$title)"
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as CarNotification

		if (packageName != other.packageName) return false
		if (key != other.key) return false
		if (title != other.title) return false
		if (summary != other.summary) return false
		if (text != other.text) return false

		return true
	}

	fun equalsKey(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as CarNotification

		if (packageName != other.packageName) return false
		if (key != other.key) return false

		return true
	}

	override fun hashCode(): Int {
		var result = packageName.hashCode()
		result = 31 * result + key.hashCode()
		result = 31 * result + (title?.hashCode() ?: 0)
		result = 31 * result + (summary?.hashCode() ?: 0)
		result = 31 * result + (text?.hashCode() ?: 0)
		return result
	}
}