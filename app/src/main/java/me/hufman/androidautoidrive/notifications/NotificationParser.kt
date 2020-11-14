package me.hufman.androidautoidrive.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat.*
import android.util.Log
import me.hufman.androidautoidrive.UnicodeCleaner

class NotificationParser(val notificationManager: NotificationManager) {
	/**
	 * Any package names that should not trigger popups
	 * Spotify, for example, shows a notification that another app is controlling it
	 */
	val SUPPRESSED_POPUP_PACKAGES = setOf("com.spotify.music")
	/** Any notification levels that should not show popups */
	val SUPPRESSED_POPUP_IMPORTANCES = setOf(IMPORTANCE_LOW, IMPORTANCE_MIN, IMPORTANCE_NONE)

	companion object {
		fun getInstance(context: Context): NotificationParser {
			val notificationManager = context.getSystemService(NotificationManager::class.java)
			return NotificationParser(notificationManager)
		}

		fun dumpNotification(title: String, sbn: StatusBarNotification, ranking: NotificationListenerService.Ranking?) {
			val extras = sbn.notification.extras
			val details = extras?.keySet()?.map { "  ${it}=>${extras.get(it)}" }?.joinToString("\n") ?: ""
			Log.i(NotificationListenerServiceImpl.TAG, "$title from ${sbn.packageName}: ${extras?.get("android.title")} with the keys:\n$details")
			Log.i(NotificationListenerServiceImpl.TAG, "Ranking: isAmbient:${ranking?.isAmbient} matchesFilter:${ranking?.matchesInterruptionFilter()}")
		}
		fun dumpMessage(title: String, bundle: Bundle) {
			val details = bundle.keySet()?.map { key -> "  ${key}=>${bundle.get(key)}" }?.joinToString("\n") ?: ""
			Log.i(NotificationListenerServiceImpl.TAG, "$title $details")
		}
	}
	/**
	 * Runs this block if the phone is Oreo or newer
	 */
	inline fun <R> ifOreo(callable: () -> R): R? {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			callable()
		} else {
			null
		}
	}

	/**
	 * Summarize an Android Notification into what should be shown in the car
	 */
	fun summarizeNotification(sbn: StatusBarNotification): CarNotification {
		var title:String? = null
		var text:String? = null
		var summary:String? = null
		val extras = sbn.notification.extras
		var icon = sbn.notification.smallIcon
		var picture: Bitmap? = null
		var pictureUri: String? = null

		// get the main title and text
		extras.getCharSequence(Notification.EXTRA_TITLE)?.let { title = it.toString() }
		extras.getCharSequence(Notification.EXTRA_TEXT)?.let { text = it.toString() }

		// full expanded view, like an email body
		extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.let { title = it.toString() }
		extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { text = it.toString() }
		extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.let { summary = it.toString() }
		extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.let { text = it.joinToString("\n") }

		// icon handling
		extras.getParcelable<Parcelable>(NotificationCompat.EXTRA_LARGE_ICON)?.let {
			// might have a user avatar, which might be an icon or a bitmap
			when (it) {
				is Icon -> icon = it
				is Bitmap -> icon = Icon.createWithBitmap(it)
			}
		}
		extras.getParcelable<Parcelable>(NotificationCompat.EXTRA_LARGE_ICON_BIG)?.let {
			// might have a user avatar, which might be an icon or a bitmap
			when (it) {
				is Icon -> icon = it
				is Bitmap -> icon = Icon.createWithBitmap(it)
			}
		}

		// maybe a picture too
		extras.getParcelable<Parcelable>(NotificationCompat.EXTRA_PICTURE)?.let {
			if (it is Bitmap) picture = it
		}

		// some extra handling for special notifications
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
				extras.getString(Notification.EXTRA_TEMPLATE) == "android.app.Notification\$MessagingStyle") {
			val parsed = summarizeMessagingNotification(sbn)
			text = parsed.text
			pictureUri = parsed.pictureUri
		}

		// use the summary if the text is empty
		text = text ?: summary

		// clean out any emoji from the notification
		title = title?.let { UnicodeCleaner.clean(it) }
		text = text?.let { UnicodeCleaner.clean(it) }

		val actions = sbn.notification.actions?.map { CarNotification.Action.parse(it) } ?: emptyList()

		val soundUri = getNotificationSound(sbn.notification)

		val summarized = CarNotification(sbn.packageName, sbn.key, icon, sbn.isClearable, actions,
				title ?: "", text?.trim() ?: "", picture, pictureUri, soundUri)
		return summarized
	}

	@RequiresApi(Build.VERSION_CODES.O)
	fun summarizeMessagingNotification(sbn: StatusBarNotification): MessagingNotificationParsed {
		val extras = sbn.notification.extras
		val historicMessages = extras.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES) ?: arrayOf()
		val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: arrayOf()
		val recentMessages = (historicMessages + messages).filterIsInstance<Bundle>().takeLast(10)
		// parse out the lines of chat
		val text = recentMessages.joinToString("\n") {
			"${it.getCharSequence("sender")}: ${it.getCharSequence("text")}"
		}
		val pictureUri = recentMessages.filter {
			it.getCharSequence("type")?.startsWith("image/") == true
		}.map {
			it.getParcelable("uri") as Uri
		}.lastOrNull()?.toString()
		return MessagingNotificationParsed(text, pictureUri)
	}

	fun getNotificationSound(notification: Notification): Uri {
		val channelSoundUri = ifOreo { getChannelSound(notification) }
		return notification.sound ?: channelSoundUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
	}

	@RequiresApi(Build.VERSION_CODES.O)
	fun getChannelSound(notification: Notification): Uri? {
		val channelId = notification.channelId
		val channel = notificationManager?.getNotificationChannel(channelId)
		return channel?.sound
	}

	fun shouldPopupNotification(sbn: StatusBarNotification?, ranking: NotificationListenerService.Ranking?): Boolean {
		if (sbn == null) return false
		if (!sbn.isClearable) return false
		if (sbn.notification.isGroupSummary()) return false
		val isMusic = sbn.notification.extras.getString(Notification.EXTRA_TEMPLATE) == "android.app.Notification\$MediaStyle"
		if (isMusic) return false
		if (SUPPRESSED_POPUP_PACKAGES.contains(sbn.packageName)) return false

		// The docs say rankingMap won't be null, but the signature isn't explicit, so check
		if (ranking != null) {
			if (ranking.isAmbient) return false
			if (!ranking.matchesInterruptionFilter()) return false

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				if (SUPPRESSED_POPUP_IMPORTANCES.contains(ranking.importance)) return false
			}
		}

		return true

	}

	fun shouldShowNotification(sbn: StatusBarNotification): Boolean {
		return !sbn.notification.isGroupSummary() &&
				sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT) != null &&
				(sbn.isClearable || sbn.notification.actions?.isNotEmpty() == true)
	}
}

data class MessagingNotificationParsed(val text: String, val pictureUri: String?)