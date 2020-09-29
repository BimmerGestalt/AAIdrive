package me.hufman.androidautoidrive.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri

class AudioPlayer(val context: Context) {
	fun playRingtone(uri: Uri?): Boolean {
		uri ?: return false
		val ringtone = RingtoneManager.getRingtone(context, uri) ?: return false
		ringtone.isLooping = false
		ringtone.audioAttributes = AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_MEDIA)
				.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
				.build()
		ringtone.play()
		return true
	}
}