package me.hufman.androidautoidrive.utils

import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import ar.com.hjg.pngj.ImageInfo
import ar.com.hjg.pngj.ImageLineInt
import ar.com.hjg.pngj.PngReaderInt
import ar.com.hjg.pngj.PngWriter
import ar.com.hjg.pngj.chunks.PngChunkPLTE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.Serializable
import java.util.zip.ZipInputStream

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
		} else {
			val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
			val canvas = Canvas(outBitmap)
			val paint = Paint()
			paint.isFilterBitmap = true
			if (invert) {
				paint.colorFilter = FILTER_NEGATIVE
			}
			val stretchToFit = Matrix()
			stretchToFit.setRectToRect(RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
					RectF(0f, 0f, width.toFloat(), height.toFloat()),
					Matrix.ScaleToFit.CENTER)
			canvas.drawBitmap(bitmap, stretchToFit, paint)
			return outBitmap
		}
	}
	fun getBitmap(drawable: Drawable, width: Int, height: Int, invert: Boolean = false): Bitmap {
		if (drawable is BitmapDrawable && !invert) {
			return getBitmap(drawable.bitmap, width, height, invert)
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
		return compressBitmapPng(getBitmap(bitmap, width, height, invert))
	}
	fun getBitmapAsPng(drawable: Drawable, width: Int, height: Int, invert: Boolean = false): ByteArray {
		return compressBitmapPng(getBitmap(drawable, width, height, invert))
	}
	fun getBitmapAsJpg(drawable: Bitmap, width: Int, height: Int, invert: Boolean = false, quality: Int = 50): ByteArray {
		return compressBitmapJpg(getBitmap(drawable, width, height, invert), quality)
	}
	fun getBitmapAsJpg(drawable: Drawable, width: Int, height: Int, invert: Boolean = false, quality: Int = 50): ByteArray {
		return compressBitmapJpg(getBitmap(drawable, width, height, invert), quality)
	}

	fun compressBitmapPng(bitmap: Bitmap): ByteArray {
		val png = ByteArrayOutputStream()
		bitmap.compress(Bitmap.CompressFormat.PNG, 0, png)
		return png.toByteArray()
	}
	fun compressBitmapJpg(bitmap: Bitmap, quality: Int): ByteArray {
		val jpg = ByteArrayOutputStream()
		bitmap.compress(Bitmap.CompressFormat.JPEG, quality, jpg)
		return jpg.toByteArray()
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

	/**
	 * Converts a 8 bit PNG file to a single channel grayscale 8 bit PNG. This supports both indexed
	 * color and RGB/RGBA PNGs. The resulting PNG is returned as a ByteArray.
	 */
	fun convertPngToGrayscale(png: ByteArray): ByteArray {
		val inputPngReader = PngReaderInt(png.inputStream())
		if (inputPngReader.imgInfo.greyscale) {
			return png
		}

		val outputImageSettings = ImageInfo(inputPngReader.imgInfo.cols, inputPngReader.imgInfo.rows, 8, false, true, false)
		val byteArrayOutputStream = ByteArrayOutputStream()
		val outputPngWriter = PngWriter(byteArrayOutputStream, outputImageSettings)
		val outputImageLine = ImageLineInt(outputImageSettings)
		val channels = inputPngReader.imgInfo.channels
		val palette = if (inputPngReader.imgInfo.indexed) {
			inputPngReader.chunksList.getById("PLTE")[0] as PngChunkPLTE
		} else {
			null
		}

		for (rowIndex in 0 until inputPngReader.imgInfo.rows) {
			val inputImageLine = inputPngReader.readRow()
			val scanLine = (inputImageLine as ImageLineInt).scanline
			for (colIndex in 0 until inputPngReader.imgInfo.cols) {
				val i = colIndex * channels
				val grayscaleRgbVal = if (palette != null) {
					getGrayscaleValue(palette.getEntry(scanLine[i]))
				} else {
					getGrayscaleValue(scanLine[i], scanLine[i + 1], scanLine[i + 2])
				}
				val grayscaleVal = if (channels == 4) { //rgba
					val alpha = scanLine[i + 3]
					(grayscaleRgbVal * (alpha / 255.0)).toInt()
				} else {
					grayscaleRgbVal
				}
				outputImageLine.scanline[colIndex] = grayscaleVal
			}
			outputPngWriter.writeRow(outputImageLine, rowIndex)
		}

		inputPngReader.end()
		outputPngWriter.end()
		return byteArrayOutputStream.toByteArray()
	}

	/**
	 * Gets the grayscale value from red, green, and blue provided values.
	 */
	private fun getGrayscaleValue(r: Int, g: Int, b: Int): Int {
		return (r * 0.299 + g * 0.587 + b * 0.114).toInt()
	}

	/**
	 * Gets the grayscale value from a RGB hexidecimal value.
	 */
	private fun getGrayscaleValue(rgbValue: Int): Int {
		val r = rgbValue shr 16
		val g = (rgbValue shr 8) and 0xff
		val b = rgbValue and 0xff
		val grayscaleVal = getGrayscaleValue(r, g, b) and 0xff
		return ((grayscaleVal shl 16) or (grayscaleVal shl 8) or grayscaleVal)
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

@Suppress("DEPRECATION")
fun Bundle.dumpToString(): String {
	return "Bundle{ " + this.keySet().map {
		"$it -> ${this.get(it)}"
	}.joinToString(", ") + " }"
}

fun Bundle.getParcelableArrayCompat(name: String): Array<Parcelable>? {
	return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
		this.getParcelableArray(name, Parcelable::class.java)
	} else {
		@Suppress("DEPRECATION")
		this.getParcelableArray(name)
	}
}
fun <T: Parcelable> Bundle.getParcelableCompat(name: String, klass: Class<T>): T? {
	return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
		this.getParcelable(name, klass)
	} else {
		@Suppress("DEPRECATION")
		this.getParcelable(name)
	}
}
fun Bundle.getSerializableCompat(name: String): Serializable? {
	return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
		this.getSerializable(name, Serializable::class.java)
	} else {
		@Suppress("DEPRECATION")
		this.getSerializable(name)
	}
}
fun <T: Parcelable> Intent.getParcelableExtraCompat(name: String, klass: Class<T>): T? =
	this.extras?.getParcelableCompat(name, klass)
fun Intent.getSerializableExtraCompat(name: String): Serializable? =
	this.extras?.getSerializableCompat(name)

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

fun String.truncate(length: Int, suffix: String = "..."): String {
	if (this.length > length) {
		return this.substring(0, length - suffix.length) + suffix
	}
	return this
}