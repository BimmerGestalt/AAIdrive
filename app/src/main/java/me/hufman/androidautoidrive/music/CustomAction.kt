package me.hufman.androidautoidrive.music

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat

class CustomAction(val packageName: String, val action: String, val name: String, val icon: Drawable?, val extras: Bundle?) {
	companion object {
		fun fromMediaCustomAction(context: Context, packageName: String, action: PlaybackStateCompat.CustomAction): CustomAction {
			val resources = context.packageManager.getResourcesForApplication(packageName)
			val icon = resources.getDrawable(action.icon, null) ?:
					Resources.getSystem().getDrawable(action.icon, null)
			return CustomAction(packageName, action.action, action.name.toString(), icon, action.extras)
		}
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as CustomAction

		if (packageName != other.packageName) return false
		if (action != other.action) return false
		if (name != other.name) return false

		return true
	}

	override fun hashCode(): Int {
		var result = packageName.hashCode()
		result = 31 * result + action.hashCode()
		result = 31 * result + name.hashCode()
		return result
	}


}
