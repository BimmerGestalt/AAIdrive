package me.hufman.androidautoidrive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon

interface PhoneAppResources {
	fun getAppIcon(packageName: String): Drawable
	fun getAppName(packageName: String): String
	fun getIconDrawable(icon: Icon): Drawable
	fun getBitmap(drawable: Drawable, width: Int, height: Int): ByteArray
	fun getBitmap(drawable: Bitmap, width: Int, height: Int): ByteArray
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
	override fun getBitmap(drawable: Drawable, width: Int, height: Int): ByteArray {
		return Utils.getBitmap(drawable, width, height)
	}
	override fun getBitmap(drawable: Bitmap, width: Int, height: Int): ByteArray {
		return Utils.getBitmap(drawable, width, height)
	}
}