package me.hufman.androidautoidrive.phoneui.controllers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.fragments.NotificationPageFragment
import me.hufman.androidautoidrive.phoneui.fragments.NotificationPageFragment.Companion.NOTIFICATION_ID
import me.hufman.androidautoidrive.phoneui.viewmodels.NotificationSettingsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.PermissionsModel

class NotificationPageController(val notificationSettingsModel: NotificationSettingsModel,
                                 val permissionsModel: PermissionsModel,
                                 val permissionsController: PermissionsController) {

	fun onChangedSwitchNotifications(isChecked: Boolean) {
		notificationSettingsModel.notificationEnabled.setValue(isChecked)
		if (isChecked) {
			// make sure we have permissions to read the notifications
			if (permissionsModel.hasNotificationPermission.value != true) {
				permissionsController.promptNotification()
			}
		}
	}

	fun sendTestNotification(view: View) {
		val context = view.context
		createNotificationChannel(context)
		val actionIntent = Intent(context, NotificationPageFragment.CustomActionListener::class.java)
		val replyInput = RemoteInput.Builder("reply")
				.setChoices(arrayOf("Yes", "No", "\uD83D\uDC4C"))
				.build()

		val action = NotificationCompat.Action.Builder(null, "Custom Action",
				PendingIntent.getBroadcast(context, 0, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
				.build()
		val inputAction = NotificationCompat.Action.Builder(null, "Reply",
				PendingIntent.getBroadcast(context, 1, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
				.addRemoteInput(replyInput)
				.build()
		val notificationBuilder = NotificationCompat.Builder(context, NotificationPageFragment.NOTIFICATION_CHANNEL_ID)
				.setSmallIcon(android.R.drawable.ic_menu_gallery)
				.setContentTitle("Test Notification")
				.setContentText("This is a test notification \ud83d\udc4d")
				.setSubText("SubText")
				.addAction(action)
				.addAction(inputAction)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			notificationBuilder.setChannelId(NotificationPageFragment.NOTIFICATION_CHANNEL_ID)
		}
		val notification = notificationBuilder.build()
		val manager = NotificationManagerCompat.from(context)
		manager.notify(NOTIFICATION_ID, notification)
	}

	private fun createNotificationChannel(context: Context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(NotificationPageFragment.NOTIFICATION_CHANNEL_ID,
					context.getString(R.string.notification_channel_test),
					NotificationManager.IMPORTANCE_DEFAULT)

			NotificationManagerCompat.from(context).createNotificationChannel(channel)
		}
	}

}