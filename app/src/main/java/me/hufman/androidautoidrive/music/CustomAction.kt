package me.hufman.androidautoidrive.music

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat

class CustomAction(val packageName: String, val action: String, var name: String, val icon: Drawable?, val extras: Bundle?) {
	companion object {
		fun fromFromCustomAction(context: Context, packageName: String, action: PlaybackStateCompat.CustomAction): CustomAction {
			val resources = context.packageManager.getResourcesForApplication(packageName)
			val icon = resources.getDrawable(action.icon, null) ?:
					Resources.getSystem().getDrawable(action.icon, null)
			return CustomAction(packageName, action.action, action.name.toString(), icon, action.extras)
		}
	}
	init {
		//format that names of the actions nicely
		if (packageName == "com.spotify.music") {
			when (action) {
				"TURN_SHUFFLE_ON" ->
					name = L.MUSIC_SPOTIFY_TURN_SHUFFLE_ON
				"TURN_REPEAT_SHUFFLE_OFF" ->
					name = L.MUSIC_SPOTIFY_TURN_SHUFFLE_OFF

				"REMOVE_FROM_COLLECTION" ->
					name = L.MUSIC_SPOTIFY_REMOVE_FROM_COLLECTION
				"ADD_TO_COLLECTION" ->
					name = L.MUSIC_SPOTIFY_ADD_TO_COLLECTION

				"START_RADIO" ->
					name = L.MUSIC_SPOTIFY_START_RADIO

				"TURN_REPEAT_ALL_ON" ->
					name = L.MUSIC_SPOTIFY_TURN_REPEAT_ALL_ON
				"TURN_REPEAT_ONE_ON" ->
					name = L.MUSIC_SPOTIFY_TURN_REPEAT_ONE_ON
				"TURN_REPEAT_ONE_OFF" ->
					name = L.MUSIC_SPOTIFY_TURN_REPEAT_ONE_OFF
			}
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