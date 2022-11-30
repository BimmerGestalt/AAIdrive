package me.hufman.androidautoidrive.phoneui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettings
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.R
import java.text.SimpleDateFormat
import java.util.*


/**
 * Track how many days the user has used the app
 */
class DayCounter(val settings: MutableAppSettings, val onDayIncremented: () -> Unit) {
	val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

	fun daysCounted(): Int {
		return settings[AppSettings.KEYS.DONATION_DAYS_COUNT].toIntOrNull() ?: 0
	}
	fun incrementDays() {
		settings[AppSettings.KEYS.DONATION_DAYS_COUNT] = (daysCounted() + 1).toString()
	}

	fun countUsage() {
		countUsage(Calendar.getInstance().time)
	}
	fun countUsage(today: Date) {
		val formattedDate = formatter.format(today)
		val previousDate = settings[AppSettings.KEYS.DONATION_LAST_DAY]
		if (previousDate != formattedDate) {
			incrementDays()
			settings[AppSettings.KEYS.DONATION_LAST_DAY] = formattedDate

			onDayIncremented()
		}
	}
}

/**
 * Track how many days the user has used the app
 * and show a notification requesting a donation at a certain threshold
 */
class DonationRequest(val context: Context) {
	companion object {
		const val DONATION_DAYS_THRESHOLD = 5   // show at the 5th day of use
		const val DONATION_URL = "https://bimmergestalt.github.io/AAIdrive/support"
		const val NOTIFICATION_CHANNEL_ID = "DonationRequest"
	}

	private val dayCounter = DayCounter(MutableAppSettingsReceiver(context)) {
		onDayIncremented()
	}

	fun countUsage() {
		dayCounter.countUsage()
	}

	private fun onDayIncremented() {
		if (dayCounter.daysCounted() == DONATION_DAYS_THRESHOLD) {
			triggerNotification()
		}
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
					context.getString(R.string.notification_channel_donation),
					NotificationManager.IMPORTANCE_MIN)
			channel.setSound(null, null)

			val notificationManager = context.getSystemService(NotificationManager::class.java)
			notificationManager.createNotificationChannel(channel)
		}
	}
	private fun triggerNotification() {
		createNotificationChannel()
		val intent = Intent(Intent.ACTION_VIEW).
				setData(Uri.parse(DONATION_URL))
		val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
				.setContentTitle(context.getText(R.string.donation_title))
				.setContentText(context.getText(R.string.donation_text))
				.setStyle(NotificationCompat.BigTextStyle()
						.setSummaryText(context.getString(R.string.donation_text_onetime)))
				.setSmallIcon(R.drawable.ic_notify)
				.setPriority(NotificationCompat.PRIORITY_LOW)
				.setAutoCancel(true)
				.setContentIntent(PendingIntent.getActivity(context, 50, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

		val notificationManager = context.getSystemService(NotificationManager::class.java)
		notificationManager.notify(50, notificationBuilder.build())
	}
}