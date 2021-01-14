package me.hufman.androidautoidrive.notifications

import android.app.Notification
import android.graphics.drawable.Drawable
import android.net.Uri

class CarNotification(val packageName: String, val key: String,     // main identifier
                      val icon: Drawable?,          // the icon in the main list
                      val isClearable: Boolean,     // swipeable
                      val actions: List<Action>,    // other actions
                      val title: String,            // first line of text
                      val text: String,             // body of notification
                      val appIcon: Drawable?,       // the icon next to the app name in Details
                      val sidePicture: Drawable?,   // an optional picture next to the title
                      val picture: Drawable?, val pictureUri: String?,    // a full picture
                      val soundUri: Uri?            // any custom audio clip
) {
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
		return "CarNotification(key='$key', title=$title, text=$text, picture=$picture)"
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