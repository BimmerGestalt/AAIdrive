package me.hufman.androidautoidrive.phoneui.fragments

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_notificationpage.*
import kotlinx.android.synthetic.main.fragment_notificationpage.swMessageNotifications
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.phoneui.controllers.PermissionsController
import me.hufman.androidautoidrive.phoneui.viewmodels.PermissionsModel
import me.hufman.androidautoidrive.phoneui.visible

class NotificationPageFragment: Fragment() {
	companion object {
		const val NOTIFICATION_CHANNEL_ID = "TestNotification"
		const val NOTIFICATION_SERVICE_TIMEOUT = 1000
	}

	val permissionsController by lazy { PermissionsController(requireActivity()) }
	val viewModel by lazy { PermissionsModel.Factory(requireContext().applicationContext).create(PermissionsModel::class.java) }
	var whenActivityStarted = 0L
	val ageOfActivity: Long
		get() = System.currentTimeMillis() - whenActivityStarted

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_notificationpage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val notificationsEnabledSetting = BooleanLiveSetting(requireContext().applicationContext, AppSettings.KEYS.ENABLED_NOTIFICATIONS)
		notificationsEnabledSetting.observe(viewLifecycleOwner) {
			swMessageNotifications.isChecked = it
			paneNotificationSettings.visible = it
		}
		swMessageNotifications.setOnCheckedChangeListener { _, isChecked ->
			onChangedSwitchNotifications(notificationsEnabledSetting, isChecked)
		}

		btnGrantSMS.setOnClickListener {
			permissionsController.promptSms()
		}

		viewModel.hasNotificationPermission.observe(viewLifecycleOwner) {
			if (ageOfActivity > NOTIFICATION_SERVICE_TIMEOUT && !it) {
				notificationsEnabledSetting.setValue(false)
			}
		}

		viewModel.hasSmsPermission.observe(viewLifecycleOwner) {
			paneSMSPermission.visible = !it
		}

		// spawn a Test notification
		btnTestNotification.setOnClickListener {
			sendTestNotification()
		}
	}

	override fun onResume() {
		super.onResume()

		whenActivityStarted = System.currentTimeMillis()
		viewModel.update()
	}

	private fun onChangedSwitchNotifications(appSetting: BooleanLiveSetting, isChecked: Boolean) {
		appSetting.setValue(isChecked)
		if (isChecked) {
			// make sure we have permissions to read the notifications
			if (viewModel.hasNotificationPermission.value != true) {
				permissionsController.promptNotification()
			}
		}
	}

	private fun sendTestNotification() {
		createNotificationChannel()
		val actionIntent = Intent(requireContext(), CustomActionListener::class.java)
		val replyInput = RemoteInput.Builder("reply")
				.setChoices(arrayOf("Yes", "No", "\uD83D\uDC4C"))
				.build()

		val action = NotificationCompat.Action.Builder(null, "Custom Action",
				PendingIntent.getBroadcast(requireContext(), 0, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT))
				.build()
		val inputAction = NotificationCompat.Action.Builder(null, "Reply",
				PendingIntent.getBroadcast(requireContext(), 1, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT))
				.addRemoteInput(replyInput)
				.build()
		val notificationBuilder = NotificationCompat.Builder(requireContext(), NOTIFICATION_CHANNEL_ID)
				.setSmallIcon(android.R.drawable.ic_menu_gallery)
				.setContentTitle("Test Notification")
				.setContentText("This is a test notification \ud83d\udc4d")
				.setSubText("SubText")
				.addAction(action)
				.addAction(inputAction)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			notificationBuilder.setChannelId(NOTIFICATION_CHANNEL_ID)
		}
		val notification = notificationBuilder.build()
		val manager = NotificationManagerCompat.from(requireContext())
		manager.notify(1, notification)
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
					getString(R.string.notification_channel_test),
					NotificationManager.IMPORTANCE_DEFAULT)

			NotificationManagerCompat.from(requireContext()).createNotificationChannel(channel)
		}
	}

	class CustomActionListener: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			if (context != null && intent != null) {
				if (RemoteInput.getResultsFromIntent(intent) != null) {
					val reply = RemoteInput.getResultsFromIntent(intent)
					Log.i(TAG, "Received reply")
					Toast.makeText(context, "Reply: ${reply.getCharSequence("reply")}", Toast.LENGTH_SHORT).show()

					val manager = NotificationManagerCompat.from(context)
					manager.cancel(1)
				} else {
					Log.i(TAG, "Received custom action")
					Toast.makeText(context, "Custom Action press", Toast.LENGTH_SHORT).show()
				}
			}
		}
	}
}