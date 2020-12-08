package me.hufman.androidautoidrive.phoneui

import android.content.Context
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

/** Resolve a Color Attribute to a color int */
@ColorInt
fun Context.getThemeColor(
		@AttrRes attrColor: Int
): Int {
	val typedValue = TypedValue()
	theme.resolveAttribute(attrColor, typedValue, true)
	val colorRes = typedValue.resourceId
	return resources.getColor(colorRes, theme)
}

/** Toggle a View's visibility by a boolean */
var View.visible: Boolean
	get() { return this.visibility == View.VISIBLE }
	set(value) {this.visibility = if (value) View.VISIBLE else View.GONE }