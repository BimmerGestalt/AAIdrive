package me.hufman.androidautoidrive

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import java.net.URL

/**
 * Methods for loading package data and graphics
 */
interface PhoneAppResources {
	fun getAppIcon(packageName: String): Drawable
	fun getAppName(packageName: String): String
	fun getBitmapDrawable(bitmap: Bitmap): Drawable
	fun getIconDrawable(icon: Icon): Drawable?
	fun getUriDrawable(uri: String): Drawable
}

class PhoneAppResourcesAndroid(val context: Context): PhoneAppResources {
	override fun getAppIcon(packageName: String): Drawable {
		return try {
			context.packageManager.getApplicationInfo(packageName, 0).loadIcon(context.packageManager)
		} catch (e: PackageManager.NameNotFoundException) {
			// very unlikely
			ColorDrawable(0)
		}
	}
	override fun getAppName(packageName: String): String {
		return try {
			context.packageManager.getApplicationInfo(packageName, 0).loadLabel(context.packageManager).toString()
		} catch (e: PackageManager.NameNotFoundException) {
			// very unlikely
			""
		}
	}

	override fun getBitmapDrawable(bitmap: Bitmap): Drawable {
		return BitmapDrawable(context.resources, bitmap)
	}
	override fun getIconDrawable(icon: Icon): Drawable? {
		return icon.loadDrawable(context)
	}

	override fun getUriDrawable(uri: String): Drawable {
		val parsedUri = Uri.parse(uri)
		val inputStream = when (parsedUri.scheme) {
			"android.resource" -> context.contentResolver.openInputStream(parsedUri)
			"content" -> context.contentResolver.openInputStream(parsedUri)
			"http" -> URL(uri).openStream()
			"https" -> URL(uri).openStream()
			else -> throw IllegalArgumentException("Unknown scheme ${parsedUri.scheme}")
		}
		val drawable = Drawable.createFromStream(inputStream, uri)
		inputStream?.close()
		return drawable
	}
}