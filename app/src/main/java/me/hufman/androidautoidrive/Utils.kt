package me.hufman.androidautoidrive

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.ByteArrayOutputStream

object Utils {
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