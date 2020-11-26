package me.hufman.androidautoidrive.phoneui.fragments

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_notificationpage.*
import kotlinx.android.synthetic.main.fragment_notificationpage.swMessageNotifications
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.TAG
import me.hufman.androidautoidrive.phoneui.UIState
import me.hufman.androidautoidrive.phoneui.visible

class NotificationPageFragment: Fragment() {
	companion object {
		const val NOTIFICATION_CHANNEL_ID = "TestNotification"
		const val NOTIFICATION_CHANNEL_NAME = "Test Notification"
		const val NOTIFICATION_SERVICE_TIMEOUT = 1000
	}

	val appSettings by lazy { MutableAppSettingsReceiver(requireContext()) }
	var whenActivityStarted = 0L

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_notificationpage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		swMessageNotifications.setOnCheckedChangeListener { _, isChecked ->
			onChangedSwitchNotifications(isChecked)
			redraw()
		}

		btnGrantSMS.setOnClickListener {
			ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.READ_SMS), 20)
		}

		// spawn a Test notification
		btnTestNotification.setOnClickListener {
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
	}

	override fun onResume() {
		super.onResume()

		whenActivityStarted = System.currentTimeMillis()

		redraw()
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
					NOTIFICATION_CHANNEL_NAME,
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

	fun onChangedSwitchNotifications(isChecked: Boolean) {
		appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS] = isChecked.toString()
		if (isChecked) {
			// make sure we have permissions to read the notifications
			if (!hasNotificationPermission() || !UIState.notificationListenerConnected) {
				promptNotificationPermission()
			}
		}
	}

	fun hasNotificationPermission(): Boolean {
		return UIState.notificationListenerConnected && NotificationManagerCompat.getEnabledListenerPackages(requireContext()).contains(requireContext().packageName)
	}

	fun promptNotificationPermission() {
		startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
	}

	fun hasSMSPermission(): Boolean {
		return (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
	}

	fun redraw() {
		val ageOfActivity = System.currentTimeMillis() - whenActivityStarted
		if (ageOfActivity > NOTIFICATION_SERVICE_TIMEOUT && !hasNotificationPermission()) {
			appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS] = "false"
		}

		swMessageNotifications.isChecked = appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS].toBoolean()
		paneNotificationSettings.visible = appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS].toBoolean()
		paneSMSPermission.visible = !hasSMSPermission()
	}

}