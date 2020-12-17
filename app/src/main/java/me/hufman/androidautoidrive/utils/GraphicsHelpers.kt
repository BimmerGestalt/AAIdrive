package me.hufman.androidautoidrive.utils

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

interface GraphicsHelpers {
	fun isDark(drawable: Drawable): Boolean
	fun compress(drawable: Drawable, width: Int, height: Int, invert: Boolean = false, quality: Int = 100): ByteArray
	fun compress(bitmap: Bitmap, width: Int, height: Int, invert: Boolean = false, quality: Int = 100): ByteArray
}

class GraphicsHelpersAndroid: GraphicsHelpers {
	override fun isDark(drawable: Drawable): Boolean {
		return Utils.isDark(drawable)
	}

	override fun compress(drawable: Drawable, width: Int, height: Int, invert: Boolean, quality: Int): ByteArray {
		return if (quality == 100) {
			Utils.getBitmapAsPng(drawable, width, height, invert)
		} else {
			Utils.getBitmapAsJpg(drawable, width, height, invert, quality)
		}
	}

	override fun compress(bitmap: Bitmap, width: Int, height: Int, invert: Boolean, quality: Int): ByteArray {
		return if (quality == 100) {
			Utils.getBitmapAsPng(bitmap, width, height, invert)
		} else {
			Utils.getBitmapAsJpg(bitmap, width, height, invert, quality)
		}
	}

}