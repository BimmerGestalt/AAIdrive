package me.hufman.androidautoidrive.music

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.res.ResourcesCompat
import java.lang.Exception

open class CustomAction(val packageName: String, val action: String, val name: String, val icon: Drawable?, val extras: Bundle?) {
	companion object {
		fun fromMediaCustomAction(context: Context, packageName: String, action: PlaybackStateCompat.CustomAction): CustomAction {
			val icon = try {
				val resources = context.packageManager.getResourcesForApplication(packageName)
				ResourcesCompat.getDrawable(resources, action.icon, null)
			} catch (e: Exception) {
				null
			}
			return formatCustomActionDisplay(
					CustomAction(packageName, action.action, action.name.toString(), icon, action.extras)
			)
		}

		fun formatCustomActionDisplay(ca: CustomAction): CustomAction {
			if(ca.packageName == "com.spotify.music")
			{
				val niceName: String

				when(ca.action)
				{
					"TURN_SHUFFLE_ON" ->
						niceName = L.MUSIC_TURN_SHUFFLE_ON
					"TURN_REPEAT_SHUFFLE_OFF" ->
						niceName = L.MUSIC_TURN_SHUFFLE_OFF
					"TURN_SHUFFLE_OFF" ->
						niceName = L.MUSIC_TURN_SHUFFLE_OFF

					"REMOVE_FROM_COLLECTION" ->
						niceName = L.MUSIC_SPOTIFY_REMOVE_FROM_COLLECTION

					"START_RADIO" ->
						niceName = L.MUSIC_SPOTIFY_START_RADIO

					"TURN_REPEAT_ALL_ON" ->
						niceName = L.MUSIC_TURN_REPEAT_ALL_ON
					"TURN_REPEAT_ONE_ON" ->
						niceName = L.MUSIC_TURN_REPEAT_ONE_ON
					"TURN_REPEAT_ONE_OFF" ->
						niceName = L.MUSIC_TURN_REPEAT_OFF
					"ADD_TO_COLLECTION" ->
						niceName = L.MUSIC_SPOTIFY_ADD_TO_COLLECTION
					"THUMB_UP" ->
						niceName = L.MUSIC_SPOTIFY_THUMB_UP
					"THUMBS_UP_SELECTED" ->
						niceName = L.MUSIC_SPOTIFY_THUMBS_UP_SELECTED
					"THUMB_DOWN" ->
						niceName = L.MUSIC_SPOTIFY_THUMB_DOWN
					"THUMBS_DOWN_SELECTED" ->
						niceName = L.MUSIC_SPOTIFY_THUMBS_DOWN_SELECTED
					"SEEK_15_SECONDS_BACK" ->
						niceName = L.MUSIC_ACTION_SEEK_BACK_15
					"SEEK_15_SECONDS_FORWARD" ->
						niceName = L.MUSIC_ACTION_SEEK_FORWARD_15
					else ->
						niceName = ca.name
				}

				return CustomAction(ca.packageName, ca.action, niceName, ca.icon, ca.extras)
			}

			if (ca.packageName == "com.jrtstudio.AnotherMusicPlayer") {
				val rocketPlayerActionPattern = Regex("([A-Za-z]+)[0-9]+")
				val match = rocketPlayerActionPattern.matchEntire(ca.name)
				if (match != null) {
					return CustomAction(ca.packageName, ca.action, match.groupValues[1], ca.icon, ca.extras)
				}
			}

			return ca
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

	override fun toString(): String {
		return "CustomAction(packageName='$packageName', action='$action', name='$name')"
	}
}

/**
 * A CustomAction that doesn't close the Actions window
 */
class CustomActionDwell(packageName: String, action: String, name: String, icon: Drawable?, extras: Bundle?): CustomAction(packageName, action, name, icon, extras)
