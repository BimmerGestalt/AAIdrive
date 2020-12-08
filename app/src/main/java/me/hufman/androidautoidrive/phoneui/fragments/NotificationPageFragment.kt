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
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.MainActivity
import me.hufman.androidautoidrive.phoneui.visible

class NotificationPageFragment: Fragment() {
	companion object {
		const val NOTIFICATION_CHANNEL_ID = "TestNotification"
		const val NOTIFICATION_CHANNEL_NAME = "Test Notification"
	}
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_notificationpage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

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
					Log.i(MainActivity.TAG, "Received reply")
					Toast.makeText(context, "Reply: ${reply.getCharSequence("reply")}", Toast.LENGTH_SHORT).show()

					val manager = NotificationManagerCompat.from(context)
					manager.cancel(1)
				} else {
					Log.i(MainActivity.TAG, "Received custom action")
					Toast.makeText(context, "Custom Action press", Toast.LENGTH_SHORT).show()
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		redraw()
	}

	fun hasSMSPermission(): Boolean {
		return (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
	}

	fun redraw() {
		paneSMSPermission.visible = !hasSMSPermission()
	}

}