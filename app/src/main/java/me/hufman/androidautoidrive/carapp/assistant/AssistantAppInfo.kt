package me.hufman.androidautoidrive.carapp.assistant

import android.graphics.drawable.Drawable
import me.hufman.androidautoidrive.music.MusicAppInfo

data class AssistantAppInfo(val name: String, val icon: Drawable,
                        val packageName: String) {

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as MusicAppInfo

		if (name != other.name) return false
		if (packageName != other.packageName) return false

		return true
	}

	override fun hashCode(): Int {
		var result = name.hashCode()
		result = 31 * result + packageName.hashCode()
		return result
	}

}