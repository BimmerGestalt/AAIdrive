package me.hufman.androidautoidrive.notifications

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri

class CarNotification(val packageName: String, val key: String, val icon: Icon, val isClearable: Boolean, val actions: List<Action>,
                      val title: String, val text: String, val picture: Bitmap?, val pictureUri: String?, val soundUri: Uri?) {
	data class Action(val name: CharSequence, val supportsReply: Boolean, val suggestedReplies: List<CharSequence>) {
		companion object {
			fun parse(action: Notification.Action): Action {
				val remoteInputs = action.remoteInputs ?: emptyArray()
				return Action(action.title,
						remoteInputs.any { it.allowFreeFormInput },
						remoteInputs.flatMap {
							it.choices?.toList() ?: emptyList()
						})
			}
		}
	}

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
		result = 31 * result + title.hashCode()
		result = 31 * result + text.hashCode()
		return result
	}
}