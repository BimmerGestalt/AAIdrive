package me.hufman.androidautoidrive.carapp.assistant

import android.content.ComponentName
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
			context.applicationContext.startActivity(intent)
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	fun getSettingsIntent(assistant: AssistantAppInfo): Intent? {
		val possibleIntents = when(assistant.packageName) {
			"com.google.android.googlequicksearchbox" -> listOf(
					Intent(Intent.ACTION_MAIN).setPackage(assistant.packageName).setComponent(ComponentName(
							assistant.packageName,
							"com.google.android.apps.gsa.settingsui.VoiceSearchPreferences"
					)))
			else -> listOf(
					Intent(Intent.ACTION_MAIN).setPackage(assistant.packageName))
		}
		return possibleIntents.firstOrNull {
			it.resolveActivity(context.packageManager) != null
		}
	}
	override fun supportsSettings(assistant: AssistantAppInfo): Boolean {
		return getSettingsIntent(assistant) != null
	}

	override fun openSettings(assistant: AssistantAppInfo) {
		getSettingsIntent(assistant)?.let {
			context.startActivity(it)
		}
	}
}