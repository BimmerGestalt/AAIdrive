package me.hufman.androidautoidrive.phoneui.controllers

import android.content.Context
import android.content.Intent
import android.net.Uri

class SendIntentController(val appContext: Context) {
	fun viewUri(uri: Uri) {
		val intent = Intent(Intent.ACTION_VIEW, uri)
				.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		appContext.startActivity(intent)
	}
}