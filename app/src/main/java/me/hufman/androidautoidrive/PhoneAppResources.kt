package me.hufman.androidautoidrive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import java.net.URL

interface PhoneAppResources {
	fun getAppIcon(packageName: String): Drawable
	fun getAppName(packageName: String): String
	fun getIconDrawable(icon: Icon): Drawable
	fun getBitmap(drawable: Drawable, width: Int, height: Int, invert: Boolean = false): ByteArray
	fun getBitmap(bitmap: Bitmap, width: Int, height: Int, invert: Boolean = false): ByteArray
	fun getBitmap(uri: String, width: Int, height: Int, invert: Boolean = false): ByteArray
}

class PhoneAppResourcesAndroid(val context: Context): PhoneAppResources {
	override fun getAppIcon(packageName: String): Drawable {
		return context.packageManager.getApplicationInfo(packageName, 0).loadIcon(context.packageManager)
	}
	override fun getAppName(packageName: String): String {
		return context.packageManager.getApplicationInfo(packageName, 0).loadLabel(context.packageManager).toString()
	}
	override fun getIconDrawable(icon: Icon): Drawable {
		return icon.loadDrawable(context)
	}
	override fun getBitmap(drawable: Drawable, width: Int, height: Int, invert: Boolean): ByteArray {
		return Utils.getBitmapAsPng(drawable, width, height, invert)
	}
	override fun getBitmap(drawable: Bitmap, width: Int, height: Int, invert: Boolean): ByteArray {
		return Utils.getBitmapAsPng(drawable, width, height, invert)
	}
	override fun getBitmap(uri: String, width: Int, height: Int, invert: Boolean): ByteArray {
		val parsedUri = Uri.parse(uri)
		val inputStream = when (parsedUri.scheme) {
			"content" -> context.contentResolver.openInputStream(parsedUri)
			"http" -> URL(uri).openStream()
			"https" -> URL(uri).openStream()
			else -> throw IllegalArgumentException("Unknown scheme ${parsedUri.scheme}")
		}
		val drawable = Drawable.createFromStream(inputStream, uri)
		inputStream.close()
		return getBitmap(drawable, width, height, invert)
	}
}