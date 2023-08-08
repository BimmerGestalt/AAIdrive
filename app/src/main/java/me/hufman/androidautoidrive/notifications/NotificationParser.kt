package me.hufman.androidautoidrive.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.Person
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat.*
import me.hufman.androidautoidrive.BuildConfig
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.PhoneAppResourcesAndroid
import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.utils.getParcelableArrayCompat
import me.hufman.androidautoidrive.utils.getParcelableCompat

class NotificationParser(val notificationManager: NotificationManager, val phoneAppResources: PhoneAppResources, val remoteViewInflater: (RemoteViews) -> View) {
	/**
	 * Any package names that should not trigger popups
	 * Spotify, for example, shows a notification that another app is controlling it
	 */
	val SUPPRESSED_POPUP_PACKAGES = setOf("com.spotify.music")
	/** Any notification levels that should not show popups */
	val SUPPRESSED_POPUP_IMPORTANCES = setOf(IMPORTANCE_LOW, IMPORTANCE_MIN, IMPORTANCE_NONE)

	val TAG = "NotificationParser"

	companion object {
		fun getInstance(context: Context): NotificationParser {
			val notificationManager = context.getSystemService(NotificationManager::class.java)
			val phoneAppResources = PhoneAppResourcesAndroid(context)
			val remoteViewInflater: (RemoteViews) -> View = { it.apply(context, null) }
			return NotificationParser(notificationManager, phoneAppResources, remoteViewInflater)
		}

		@Suppress("DEPRECATION")
		fun dumpNotification(title: String, sbn: StatusBarNotification, ranking: NotificationListenerService.Ranking?) {
			val extras = sbn.notification.extras
			val details = extras?.keySet()?.map { "  ${it}=>${extras.get(it)}" }?.joinToString("\n") ?: ""
			Log.i(NotificationListenerServiceImpl.TAG, "$title from ${sbn.packageName}: ${extras?.get("android.title")} with the keys:\n$details")
			Log.i(NotificationListenerServiceImpl.TAG, "Ranking: isAmbient:${ranking?.isAmbient} matchesFilter:${ranking?.matchesInterruptionFilter()}")
		}
		@Suppress("DEPRECATION")
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
		val appName = phoneAppResources.getAppName(sbn.packageName)
		val appIcon = phoneAppResources.getIconDrawable(sbn.notification.smallIcon)
		var icon = appIcon
		var sidePicture: Drawable? = null
		var picture: Drawable? = null
		var pictureUri: String? = null

		if (sbn.notification.getContentView() != null) {
			val parsed = summarizedCustomNotification(sbn)
			if (parsed != null) {
				return parsed
			}
		}

		// get the main title and text
		extras.getCharSequence(Notification.EXTRA_TITLE)?.let { title = it.toString() }
		extras.getCharSequence(Notification.EXTRA_TEXT)?.let { text = it.toString() }

		// full expanded view, like an email body
		extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.let { title = it.toString() }
		extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { text = it.toString() }
		extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.let { summary = it.toString() }
		extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.let { text = it.joinToString("\n") }

		// icon handling
		extras.getParcelableCompat(NotificationCompat.EXTRA_LARGE_ICON, Parcelable::class.java)?.also { largeIcon ->
			// the picture on the side of a notification
			when (largeIcon) {
				is Icon -> phoneAppResources.getIconDrawable(largeIcon).also {
					icon = it
					sidePicture = it
				}
				is Bitmap -> phoneAppResources.getBitmapDrawable(largeIcon).also {
					icon = it
					sidePicture = it
				}
			}
		}
		extras.getParcelableCompat(NotificationCompat.EXTRA_LARGE_ICON_BIG, Parcelable::class.java)?.also { largeIcon ->
			// the picture on the side of a notification, when expanded
			when (largeIcon) {
				is Icon -> phoneAppResources.getIconDrawable(largeIcon).also {
					icon = it
					sidePicture = it
				}
				is Bitmap -> phoneAppResources.getBitmapDrawable(largeIcon).also {
					icon = it
					sidePicture = it
				}
			}
		}

		// maybe a picture too
		extras.getParcelableCompat(NotificationCompat.EXTRA_PICTURE, Parcelable::class.java)?.let {
			if (it is Bitmap) picture = phoneAppResources.getBitmapDrawable(it)
		}

		// some extra handling for special notifications
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
				extras.getString(Notification.EXTRA_TEMPLATE) == "android.app.Notification\$MessagingStyle") {
			val parsed = summarizeMessagingNotification(sbn)
			text = parsed.text
			parsed.icon?.let { phoneAppResources.getIconDrawable(it) }?.also {
				icon = it
				sidePicture = it
			}
			pictureUri = parsed.pictureUri
		}

		// use the summary if the text is empty
		text = text ?: summary

		// clean out any emoji from the notification
		title = title?.let { UnicodeCleaner.clean(it) }
		text = text?.let { UnicodeCleaner.clean(it) }

		val actions = sbn.notification.actions
				?.filter {it.title?.isNotBlank() == true}
				?.map { CarNotification.Action.parse(it) } ?: emptyList()

		val soundUri = getNotificationSound(sbn.notification)

		val summarized = CarNotification(sbn.packageName, sbn.key, appName, icon, sbn.isClearable, actions,
				title ?: "", text?.trim() ?: "",
				appIcon, sidePicture, picture, pictureUri, soundUri)
		return summarized
	}

	@RequiresApi(Build.VERSION_CODES.O)
	fun summarizeMessagingNotification(sbn: StatusBarNotification): MessagingNotificationParsed {
		val extras = sbn.notification.extras
		val historicMessages = extras.getParcelableArrayCompat(Notification.EXTRA_HISTORIC_MESSAGES) ?: arrayOf()
		val messages = extras.getParcelableArrayCompat(Notification.EXTRA_MESSAGES) ?: arrayOf()
		val recentMessages = (historicMessages + messages).filterIsInstance<Bundle>().takeLast(10)
		// parse out the lines of chat
		val text = recentMessages.joinToString("\n") {
			"${it.getCharSequence("sender")}: ${it.getCharSequence("text")}"
		}
		val personIcon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {   // person objects only exist in >= Pie
			val person = recentMessages.lastOrNull()?.getParcelableCompat("sender_person", Person::class.java) // last message person
			?: extras.getParcelableCompat(Notification.EXTRA_MESSAGING_PERSON, Person::class.java)
			person?.icon
		} else null
		val pictureUri = recentMessages.filter {
			it.getCharSequence("type")?.startsWith("image/") == true
		}.mapNotNull {
			it.getParcelableCompat("uri", Uri::class.java)
		}.lastOrNull()?.toString()
		return MessagingNotificationParsed(text, personIcon, pictureUri)
	}

	fun summarizedCustomNotification(sbn: StatusBarNotification): CarNotification? {
		val appName = phoneAppResources.getAppName(sbn.packageName)
		val appIcon = phoneAppResources.getIconDrawable(sbn.notification.smallIcon)
		val smallIcon = phoneAppResources.getIconDrawable(sbn.notification.smallIcon)
		val extras = sbn.notification.extras
		val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""

		val customViewTemplate = sbn.notification.getContentView() ?: return null
		val customView = try {
			remoteViewInflater.invoke(customViewTemplate)
		} catch (e: SecurityException) {
			// Can't inflate the Custom View
			Log.e(TAG, "Could not inflate custom view for notification $appName $title", e)
			return null
		} catch (e: RemoteViews.ActionException) {
			// Can't inflate the Custom View
			Log.e(TAG, "Could not inflate custom view for notification $appName $title", e)
			return null
		} catch (e: ClassCastException) {
			// Can't inflate the Custom View
			Log.e(TAG, "Could not inflate custom view for notification $appName $title", e)
			return null
		} catch (e: Resources.NotFoundException) {
			// Can't inflate the Custom View
			Log.e(TAG, "Could not inflate custom view for notification $appName $title", e)
			return null
		}

		// find elements from the custom view
		val images = customView.collectChildren().filterIsInstance<ImageView>().toList()
		val drawable = images.sortedByDescending { it.width * it.height }
			.getOrNull(0)?.drawable
		val sidePicture = drawable.ifMatches { it.intrinsicHeight <= 200 }
		val picture = drawable.ifMatches { it.intrinsicHeight > 200 }
		val lines = customView.collectChildren().filterIsInstance<TextView>()
			.filter { ! it.isClickable }
			.map { it.text.toString() }
			.filter { it.isNotEmpty() }
			.toList()
		val actions = customView.collectChildren().filterIsInstance<TextView>()
			.filter { it.isClickable && it.text?.isNotBlank() == true }
			.map { CarNotification.Action(it.text.toString(), false, emptyList()) }
			.take(5).toList()

		return CarNotification(sbn.packageName, sbn.key, appName, smallIcon, sbn.isClearable,
				actions, title, lines.joinToString("\n"), appIcon, sidePicture, picture, null,
				getNotificationSound(sbn.notification))
	}

	@Suppress("DEPRECATION")
	fun getNotificationSound(notification: Notification): Uri {
		val channelSoundUri = ifOreo { getChannelSound(notification) }
		return notification.sound ?: channelSoundUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
	}

	@RequiresApi(Build.VERSION_CODES.O)
	fun getChannelSound(notification: Notification): Uri? {
		val channelId = notification.channelId
		val channel = notificationManager.getNotificationChannel(channelId)
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
		val myMainServiceNotification = sbn.packageName == BuildConfig.APPLICATION_ID && sbn.isOngoing
		return !sbn.notification.isGroupSummary() &&
				sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE) != null &&
				(sbn.isClearable || sbn.notification.actions?.isNotEmpty() == true || sbn.notification.getContentView() != null) &&
				!myMainServiceNotification
	}
}

data class MessagingNotificationParsed(val text: String, val icon: Icon?, val pictureUri: String?)

fun Drawable?.ifMatches(matches: (Drawable) -> Boolean): Drawable? {
	return if (this != null && matches(this)) {
		this
	} else {
		null
	}
}

fun View.collectChildren(matches: (View) -> Boolean = { true }): Sequence<View> {
	return if (this is ViewGroup) {
		(0 until childCount).asSequence().map { index ->
			getChildAt(index).collectChildren(matches)
		}.flatten()
	} else {
		if (matches(this)) {
			sequenceOf(this)
		} else {
			emptySequence()
		}
	}
}

@Suppress("DEPRECATION")
fun Notification.getContentView(): RemoteViews? {
	return this.bigContentView ?: this.contentView
}
