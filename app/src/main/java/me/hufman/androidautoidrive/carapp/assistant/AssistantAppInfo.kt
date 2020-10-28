package me.hufman.androidautoidrive.carapp.assistant

import android.graphics.drawable.Drawable
import me.hufman.androidautoidrive.carapp.AMAppInfo
import me.hufman.androidautoidrive.carapp.AMCategory
import me.hufman.androidautoidrive.music.MusicAppInfo

data class AssistantAppInfo(override val name: String, override val icon: Drawable,
                            override val packageName: String): AMAppInfo {

	override val category = AMCategory.ONLINE_SERVICES

	override val amAppIdentifier: String
		get() = "androidautoidrive.assistant.${this.packageName}"

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