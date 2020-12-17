package me.hufman.androidautoidrive.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.SystemClock

class AudioPlayer(val context: Context) {
	companion object {
		const val PLAYBACK_DEBOUNCE = 4000L
	}
	var timeLastPlayed = 0L
	val timeSinceLastPlayed: Long
		get() = SystemClock.elapsedRealtime() - timeLastPlayed

	fun playRingtone(uri: Uri?): Boolean {
		if (timeSinceLastPlayed < PLAYBACK_DEBOUNCE) return false
		uri ?: return false
		val ringtone = RingtoneManager.getRingtone(context, uri) ?: return false
		ringtone.isLooping = false
		ringtone.audioAttributes = AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_MEDIA)
				.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
				.build()
		ringtone.play()
		timeLastPlayed = SystemClock.elapsedRealtime()
		return true
	}
}