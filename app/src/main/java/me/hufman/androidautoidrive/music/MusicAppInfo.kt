package me.hufman.androidautoidrive.music

import android.graphics.drawable.Drawable

data class MusicAppInfo(val name: String, val icon: Drawable,
                        val packageName: String, val className: String) {
	var probed = false
	var connectable = false
	var browseable = false
	var searchable = false

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as MusicAppInfo

		if (name != other.name) return false
		if (packageName != other.packageName) return false
		if (className != other.className) return false

		return true
	}

	override fun hashCode(): Int {
		var result = name.hashCode()
		result = 31 * result + packageName.hashCode()
		result = 31 * result + className.hashCode()
		return result
	}

	fun toMap():Map<String, Any> {
		return mapOf(
				"name" to name,
				"packageName" to packageName,
				"className" to className,
				"connectable" to connectable,
				"browseable" to browseable,
				"searchable" to searchable
		)
	}
}