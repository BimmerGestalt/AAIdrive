package me.hufman.androidautoidrive

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withTimeout
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import me.hufman.idriveconnectionkit.rhmi.RHMIProperty
import java.io.ByteArrayOutputStream
import kotlin.math.abs

object Utils {
	fun getBitmap(bitmap: Bitmap, width: Int, height: Int): ByteArray {
		if (bitmap.width == width && bitmap.height == height) {
			return compressBitmap(bitmap)
		} else {
			val resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)
			return compressBitmap(resizedBitmap)
		}
	}
	fun getBitmap(drawable: Drawable, width: Int, height: Int): ByteArray {
		if (drawable is BitmapDrawable) {
			val bmp = Bitmap.createScaledBitmap(drawable.bitmap, 48, 48, true)
			return compressBitmap(bmp)
		} else {
			val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
			val canvas = Canvas(bitmap)
			drawable.setBounds(0, 0, width, height)
			drawable.draw(canvas)
			return compressBitmap(bitmap)
		}
	}

	fun compressBitmap(bitmap: Bitmap): ByteArray {
		val png = ByteArrayOutputStream()
		bitmap.compress(Bitmap.CompressFormat.PNG, 0, png)
		return png.toByteArray()
	}

	fun etchAsInt(obj: Any?): Int {
		/** Etch likes to shrink numbers to the smallest type that will fit
		 * But JVM wants the number types to match in various places
		 */
		return when (obj) {
			is Byte -> obj.toInt()
			is Short -> obj.toInt()
			is Int -> obj
			else -> 0
		}
	}
}

inline fun <T> MutableList<T>.removeFirst(predicate: (T) -> Boolean): T {
	val index = this.indexOfFirst(predicate)
	val element = this.removeAt(index)
	return element
}
inline fun <T> MutableList<T>.removeFirstOrNull(predicate: (T) -> Boolean): T? {
	val index = this.indexOfFirst(predicate)
	return if (index >= 0) {
		val element = this.removeAt(index)
		element
	} else {
		null
	}
}

inline fun Bundle.dumpToString(): String {
	return "Bundle{ " + this.keySet().map {
		"$it -> ${this.get(it)}"
	}.joinToString(", ") + " }"
}

/** Wait for a Deferred to resolve
 * if it takes longer than the timeout, run the timeoutHandler then continue waiting
 */
suspend inline fun <T> Deferred<T>.awaitPending(timeout: Long, timeoutHandler: () -> Unit): T {
	val deferred = this
	try {
		return withTimeout(timeout) {
			val result = deferred.await()
			result
		}
	} catch (ex: CancellationException) {
		timeoutHandler()
		return deferred.await()
	}
}
suspend inline fun <T> Deferred<T>.awaitPending(timeout: Int, timeoutHandler: () -> Unit): T {
	return awaitPending(timeout.toLong(), timeoutHandler)
}

/**
 * Iterate through the given components to find the component that is
 * horizontally aligned, to the right, to the component matched by the predicate
 */
fun findAdjacentComponent(components: Iterable<RHMIComponent>, predicate: (RHMIComponent) -> Boolean): RHMIComponent? {
	val found = components.firstOrNull(predicate) ?: return null
	val layout = getComponentLayout(found)
	val foundLocation = getComponentLocation(found, layout)
	val neighbors = components.filter {
		val location = getComponentLocation(it, layout)
		abs(foundLocation.second - location.second) < 10 && // same height
				foundLocation.first < location.first    // first compnent left of second
	}
	return neighbors.sortedBy {
		getComponentLocation(it, layout).first
	}.firstOrNull()
}

fun getComponentLayout(component: RHMIComponent): Int {
	val xProperty = component.properties.get(20)
	return if (xProperty is RHMIProperty.LayoutBag) {
		xProperty.values.keys.firstOrNull { (xProperty.values[it] as Int) < 1600 } ?: 0
	} else {
		0
	}
}
fun getComponentLocation(component: RHMIComponent, layout: Int = 0): Pair<Int, Int> {
	val xProperty = component.properties[20]
	val yProperty = component.properties[21]
	val x = when (xProperty) {
		is RHMIProperty.SimpleProperty -> xProperty.value as Int
		is RHMIProperty.LayoutBag -> xProperty.get(layout) as Int
		else -> -1
	}
	val y = when (yProperty) {
		is RHMIProperty.SimpleProperty -> yProperty.value as Int
		is RHMIProperty.LayoutBag -> yProperty.get(layout) as Int
		else -> -1
	}
	return Pair(x,y)
}
