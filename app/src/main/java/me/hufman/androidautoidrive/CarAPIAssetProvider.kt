package me.hufman.androidautoidrive

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.util.Log
import java.io.FileNotFoundException

class CarAPIAssetProvider : ContentProvider() {
	companion object {
		const val TAG = "CarApiAssets"
	}

	override fun insert(uri: Uri?, values: ContentValues?): Uri? {
		return null
	}

	override fun onCreate(): Boolean {
		return false
	}

	override fun update(uri: Uri?, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
		return 0
	}

	override fun delete(uri: Uri?, selection: String?, selectionArgs: Array<out String>?): Int {
		return 0
	}

	override fun getType(uri: Uri?): String {
		Log.i(TAG, "getType($uri)")
		startService()
		return "assets_provider"
	}

	override fun query(uri: Uri?, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
		Log.i(TAG, "query(uri=$uri, selection=$selection)")
		return null
	}

	override fun openAssetFile(uri: Uri?, mode: String?): AssetFileDescriptor? {
		Log.i(TAG, "openAssetFile(uri=$uri, mode=$mode")
		startService()
		throw FileNotFoundException(uri?.toString())
	}

	private fun startService() {
		Log.i(TAG, "Sensed the Connected app sniffing around, starting the service")
		context.startService(Intent(context, MainService::class.java).setAction(MainService.ACTION_START))
	}
}