package me.hufman.androidautoidrive.carapp.assistant

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.util.Log
import me.hufman.androidautoidrive.PhoneAppResources

class AssistantControllerAndroid(val context: Context, val phoneAppResources: PhoneAppResources): AssistantController {
	val TAG = "AssistantController"

	override fun getAssistants(): Set<AssistantAppInfo> {
		val intent = Intent(Intent.ACTION_VOICE_COMMAND)
		val resolved = context.packageManager.queryIntentActivities(intent, 0)
		resolved.forEach {
			Log.i(TAG, "Found voice assistant: ${it.activityInfo.packageName}")
		}
		return resolved.map {
			val appInfo = it.activityInfo.applicationInfo
			val name = context.packageManager.getApplicationLabel(appInfo).toString()
			AssistantAppInfo(name, phoneAppResources.getAppIcon(appInfo.packageName), appInfo.packageName)
		}.toSet()
	}

	override fun triggerAssistant(assistant: AssistantAppInfo) {
		val intent = Intent(Intent.ACTION_VOICE_COMMAND)
		intent.setPackage(assistant.packageName)
		intent.setFlags(FLAG_ACTIVITY_NEW_TASK)
		try {
			context.startActivity(intent)
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

}