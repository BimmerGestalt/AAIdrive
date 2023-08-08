package me.hufman.androidautoidrive.carapp.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.hufman.androidautoidrive.ApplicationCallbacks
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.utils.PackageManagerCompat.queryIntentActivitiesCompat
import me.hufman.androidautoidrive.utils.Utils

open class AssistantControllerAndroid(val context: Context, val phoneAppResources: PhoneAppResources): AssistantController {
	companion object {
		val TAG = "AssistantController"

		val NOTIFICATION_ID = 20506
		val NOTIFICATION_CHANNEL_ID = "AssistantLauncher"

		fun getVoiceIntent(): Intent {
			return Intent(Intent.ACTION_VOICE_COMMAND)
		}
	}

	init {
		createNotificationChannel()
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
					context.getString(R.string.notification_channel_assistant),
					NotificationManager.IMPORTANCE_HIGH)
			channel.setSound(null, null)

			val notificationManager = context.getSystemService(NotificationManager::class.java)
			notificationManager.createNotificationChannel(channel)
		}
	}

	override fun getAssistants(): Set<AssistantAppInfo> {
		val intent = getVoiceIntent()
		val resolved = context.packageManager.queryIntentActivitiesCompat(intent, 0)
		resolved.forEach {
			Log.i(TAG, "Found voice assistant: ${it.activityInfo.packageName}")
		}
		return resolved.map {
			val appInfo = it.activityInfo.applicationInfo
			val name = context.packageManager.getApplicationLabel(appInfo).toString()
			AssistantAppInfo(name, phoneAppResources.getAppIcon(appInfo.packageName), appInfo.packageName)
		}.toSet()
	}

	/**
	 * Triggers this given assistant in the appropriate manner for this version of Android
	 */
	override fun triggerAssistant(assistant: AssistantAppInfo) {
		val intent = getVoiceIntent()
		intent.setPackage(assistant.packageName)
		intent.setFlags(FLAG_ACTIVITY_NEW_TASK)

		// triggerNow only works on Android <10, or if the app itself is on screen
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P || ApplicationCallbacks.visibleWindows.toInt() > 0) {
			Log.i(TAG, "Triggering voice assistant ${assistant.packageName} directly")
			triggerNow(intent)
		} else {
			Log.i(TAG, "Triggering voice assistant ${assistant.packageName} through a Full Screen Intent")
			triggerFullScreen(assistant, intent)
		}
	}

	/**
	 * Triggers this assistant with the naive startActivity method
	 */
	fun triggerNow(intent: Intent) {
		try {
			context.applicationContext.startActivity(intent)
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	/**
	 * Triggers a Full Screen Notification, which is needed to trigger the Assistant while the screen is off
	 * This function is meant to be triggered via the car's UI, typically while the screen is off
	 * If the screen is on, the Full Screen Notification will show as a Heads Up popup
	 * and require that the user interact with it
	 */
	fun triggerFullScreen(assistant: AssistantAppInfo, intent: Intent) {
		val pendingIntent = PendingIntent.getActivity(context.applicationContext,
				0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

		val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
				.setAutoCancel(true)
				.setSmallIcon(R.drawable.ic_notify)
				.setLargeIcon(Utils.getBitmap(assistant.icon, 192, 192))
				.setContentTitle(context.getString(R.string.notification_assistant_tap, assistant.name))
				.setContentText(context.getString(R.string.notification_assistant_description))
				.setPriority(NotificationCompat.PRIORITY_HIGH)
				.setCategory(NotificationCompat.CATEGORY_ALARM)     // shows even in DND mode
				.setFullScreenIntent(pendingIntent, true)
				.setTimeoutAfter(5000)
		NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notificationBuilder.build())
	}

	fun getSettingsIntent(assistant: AssistantAppInfo): Intent? {
		val possibleIntents = when(assistant.packageName) {
			"com.google.android.googlequicksearchbox" -> listOf(
					Intent(Intent.ACTION_MAIN).setPackage(assistant.packageName).setComponent(ComponentName(
							assistant.packageName,
							"com.google.android.apps.gsa.settingsui.VoiceSearchPreferences"
					)),
					Intent(Intent.ACTION_MAIN).setPackage(assistant.packageName))
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
			try {
				context.startActivity(it)
			} catch (e: ActivityNotFoundException) {
				Log.w(TAG, "Assistant Settings Intent failed", e)
			}
		}
	}
}