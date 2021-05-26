package me.hufman.androidautoidrive.phoneui

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.view.marginTop
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import me.hufman.androidautoidrive.phoneui.ViewHelpers.scrollTop

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

object ViewHelpers {
	/** Toggle a View's visibility by a boolean */
	var View.visible: Boolean
		get() {
			return this.visibility == View.VISIBLE
		}
		set(value) {
			this.visibility = if (value) View.VISIBLE else View.GONE
		}

	/** Find the parent element that matches this predicate */
	fun View.findParent(predicate: (ViewGroup) -> Boolean): ViewGroup? {
		val parent = this.parent as? ViewGroup ?: return null
		if (predicate(parent)) return parent
		return parent.findParent(predicate)
	}

	/** Get the top padding of a scrollview */
	val ScrollView.calculatedTopPadding: Int
		get() {
			val selfPadding = this.marginTop + this.paddingTop
			val childPadding = this.getChildAt(0).marginTop + this.getChildAt(0).paddingTop
			return selfPadding + childPadding
		}

	/** Get the onscreen position of this view, counting all parents' offsets */
	val View.scrollTop: Int
		get() {
			val parent = this.parent ?: return 0
			if (parent is View && parent !is ScrollView) {
				return parent.scrollTop + this.top
			}
			return this.top
		}
	val View.scrollBottom: Int
		get() {
			println("Finding scrollBottom with scrollTop ${this.scrollTop} and height ${this.height}")
			return this.scrollTop + this.height
		}

	/** Toggle between two views depending on a boolean */
	fun showEither(falseView: View, trueView: View, determiner: () -> Boolean) {
		showEither(falseView, trueView, { true }, determiner)
	}

	fun showEither(falseView: View, trueView: View, prereq: () -> Boolean, determiner: () -> Boolean) {
		val prereqed = prereq()
		val determination = determiner()
		falseView.visible = prereqed && !determination
		trueView.visible = prereqed && determination
	}
}

object LiveDataHelpers {
	// Inspired by https://github.com/tmurakami/aackt/blob/master/lifecycle-livedata/src/main/java/com/github/tmurakami/aackt/lifecycle/Transformations.kt
	/** Like Transformations.map(LiveData), except supports an initial value */
	inline fun <T, R : Any> LiveData<T>.map(initialValue: R? = null, crossinline block: (T) -> R?): LiveData<R> {
		val result = MediatorLiveData<R>()
		initialValue?.also { result.value = it }
		result.addSource(this) {
			// only map nonnull data
			it?.also { result.value = block(it) }
		}
		return result
	}

	/**
	 * Combine this LiveData and given LiveData to some calculated output LiveData
	 *     Either LiveData object will trigger an update
	 */
	fun <X,Y,R> LiveData<X>.combine(other: LiveData<Y>, combiner: (X, Y) -> R): LiveData<R> {
		val result = MediatorLiveData<R>()
		result.addSource(this) { x ->
			x ?: return@addSource
			val y = other.value ?: return@addSource
			result.value = combiner(x, y)
		}
		result.addSource(other) { y ->
			val x = this.value ?: return@addSource
			y ?: return@addSource
			result.value = combiner(x, y)
		}
		return result
	}

	/**
	 * Formats this LiveData<Double> with the given format string
	 */
	fun LiveData<Double>.format(format: String): LiveData<String> {
		val result = MediatorLiveData<String>()
		result.addSource(this) {
			val value = this.value
			if (value != null) {
				result.value = String.format(format, value)
			}
		}
		return result
	}

	/**
	 * Decorates a given LiveData<String> with a unit, where the unit comes from a LiveData<Context.() -> String>
	 *     Either LiveData object will trigger an update
	 */
	fun LiveData<String>.addUnit(unit: LiveData<Context.() -> String>): LiveData<Context.() -> String> {
		val result = MediatorLiveData<Context.() -> String>()
		result.value = {""}
		result.addSource(this) {
			result.value = {
				val units = unit.value?.let { this.run(it) } ?: ""
				"$it $units"
			}
		}
		result.addSource(unit) {
			val value = this@addUnit.value
			if (value != null) {
				result.value = {
					val units = unit.value?.let { this.run(it) } ?: ""
					"$value $units"
				}
			}
		}
		return result
	}
}

/**
 * A LiveData that sets its value to the producer output whenever it becomes active
 */
class FunctionalLiveData<T>(val producer: () -> T?): LiveData<T>() {
	override fun onActive() {
		super.onActive()
		value = producer()
	}
}