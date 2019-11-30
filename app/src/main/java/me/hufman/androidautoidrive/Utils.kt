package me.hufman.androidautoidrive

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.annotation.AttrRes
import android.support.annotation.ColorInt
import android.util.TypedValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withTimeout
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import me.hufman.idriveconnectionkit.rhmi.RHMIProperty
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.math.abs

object Utils {
	val FILTER_NEGATIVE by lazy {
		ColorMatrixColorFilter(floatArrayOf(
				-1.0f,     0f,     0f,    0f, 255f, // red
				0f,  -1.0f,     0f,    0f, 255f, // green
				0f,     0f,  -1.0f,    0f, 255f, // blue
				0f,     0f,     0f,  1.0f,   0f  // alpha
		))
	}
	val FILTER_BLACKMASK_VALUES = floatArrayOf(
		1.0f,     0f,     0f,    0f,   0f, // red
		  0f,   1.0f,     0f,    0f,   0f, // green
		  0f,     0f,   1.0f,    0f,   0f, // blue
		1.0f,   1.0f,   1.0f,    0f,   0f  // alpha
	)
	val FILTER_BLACKMASK by lazy {
		ColorMatrixColorFilter(FILTER_BLACKMASK_VALUES)
	}

	fun getBitmap(bitmap: Bitmap, width: Int, height: Int, invert: Boolean = false): Bitmap {
		if (bitmap.width == width && bitmap.height == height && invert == false) {
			return bitmap
		} else if (invert == false) {
			return Bitmap.createScaledBitmap(bitmap, width, height, true)
		} else {
			val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
			val canvas = Canvas(outBitmap)
			val paint = Paint()
			paint.isFilterBitmap = true
			if (invert) {
				paint.colorFilter = FILTER_NEGATIVE
			}
			canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), Rect(0, 0, width, height), paint)
			return outBitmap
		}
	}
	fun getBitmap(drawable: Drawable, width: Int, height: Int, invert: Boolean = false): Bitmap {
		if (drawable is BitmapDrawable && !invert) {
			return getBitmap(drawable.bitmap, 48, 48, invert)
		} else {
			val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
			val canvas = Canvas(bitmap)
			drawable.setBounds(0, 0, width, height)
			if (invert) {
				drawable.colorFilter = FILTER_NEGATIVE
			}
			drawable.draw(canvas)
			return bitmap
		}
	}
	fun getBitmapAsPng(bitmap: Bitmap, width: Int, height: Int, invert: Boolean = false): ByteArray {
		return compressBitmap(Utils.getBitmap(bitmap, width, height, invert))
	}
	fun getBitmapAsPng(drawable: Drawable, width: Int, height: Int, invert: Boolean = false): ByteArray {
		return compressBitmap(getBitmap(drawable, width, height, invert))
	}

	fun compressBitmap(bitmap: Bitmap): ByteArray {
		val png = ByteArrayOutputStream()
		bitmap.compress(Bitmap.CompressFormat.PNG, 0, png)
		return png.toByteArray()
	}

	val darkCache = HashMap<Int, Boolean>()
	fun isDark(drawable: Drawable): Boolean {
		return darkCache[drawable.hashCode()] ?: calculateDark(drawable).apply { darkCache[drawable.hashCode()] = this }
	}
	fun calculateDark(drawable: Drawable): Boolean {
		var visiblePixels = 0
		var darkPixels = 0
		val bmp = getBitmap(drawable, 48, 48)
		val allPixels = IntArray(bmp.width * bmp.height)
		var hsv = FloatArray(3)
		bmp.getPixels(allPixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
		allPixels.filter{ Color.alpha(it) > 100 }.forEach {
			visiblePixels++
			Color.colorToHSV(it, hsv)
			if (hsv[2] < 0.2) darkPixels++
		}
		return 1.0 * darkPixels / visiblePixels > .5
	}

	fun getIconMask(tint: Int): ColorFilter {
		val values = FILTER_BLACKMASK_VALUES.clone()
		values[0] = values[0] * Color.red(tint) / 255f
		values[6] = values[6] * Color.green(tint) / 255f
		values[12] = values[12] * Color.blue(tint) / 255f
		return ColorMatrixColorFilter(values)
	}

	fun etchAsInt(obj: Any?, default: Int = 0): Int {
		/** Etch likes to shrink numbers to the smallest type that will fit
		 * But JVM wants the number types to match in various places
		 */
		return when (obj) {
			is Byte -> obj.toInt()
			is Short -> obj.toInt()
			is Int -> obj
			else -> default
		}
	}

	fun loadZipfile(zipfile: InputStream?): Map<String, ByteArray> {
		zipfile ?: return mapOf()
		val contents = HashMap<String, ByteArray>()
		val imageStream = ZipInputStream(zipfile)
		while (true) {
			val next = imageStream.nextEntry ?: break
			contents[next.name] = imageStream.readBytes()
		}
		return contents
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

fun Bundle.dumpToString(): String {
	return "Bundle{ " + this.keySet().map {
		"$it -> ${this.get(it)}"
	}.joinToString(", ") + " }"
}

/** Wait for a Deferred to resolve
 * if it takes longer than the timeout, run the timeoutHandler then continue waiting
 */
suspend inline fun <T> Deferred<T>.awaitPending(timeout: Long, timeoutHandler: () -> Unit): T {
	val deferred = this
	return try {
		return withTimeout(timeout) {
			deferred.await()
		}
	} catch (ex: CancellationException) {
		timeoutHandler()
		deferred.await()
	}
}
suspend inline fun <T> Deferred<T>.awaitPending(timeout: Int, timeoutHandler: () -> Unit): T {
	return awaitPending(timeout.toLong(), timeoutHandler)
}

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
