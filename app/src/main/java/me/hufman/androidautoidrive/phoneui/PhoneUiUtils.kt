package me.hufman.androidautoidrive.phoneui

import android.content.Context
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

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


/** Toggle between two views depending on a boolean */
fun showEither(falseView: View, trueView: View, determiner: () -> Boolean) {
	showEither(falseView, trueView, {true}, determiner)
}

fun showEither(falseView: View, trueView: View, prereq: () -> Boolean, determiner: () -> Boolean) {
	val prereqed = prereq()
	val determination = determiner()
	falseView.visible = prereqed && !determination
	trueView.visible = prereqed && determination
}

// Inspired by https://github.com/tmurakami/aackt/blob/master/lifecycle-livedata/src/main/java/com/github/tmurakami/aackt/lifecycle/Transformations.kt
/** Like Transformations.map(LiveData), except supports an initial value */
inline fun <T, R: Any> LiveData<T>.map(initialValue: R?, crossinline block: (T) -> R?): LiveData<R> {
	val result = MediatorLiveData<R>()
	result.value = initialValue
	result.addSource(this) {
		// only map nonnull data
		it?.run { result.value = block(it) }
	}
	return result
}