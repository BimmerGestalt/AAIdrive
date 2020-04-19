package me.hufman.carprojection

import android.graphics.drawable.Drawable

data class ProjectionAppInfo(val packageName: String, val className: String, val name: String, val icon: Drawable) {
	override fun toString(): String {
		return "ProjectionAppInfo(name='$name', className='$className')"
	}
}