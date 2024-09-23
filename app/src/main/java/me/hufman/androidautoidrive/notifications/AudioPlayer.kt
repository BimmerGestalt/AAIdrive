package me.hufman.androidautoidrive.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import me.hufman.androidautoidrive.carapp.notifications.TAG
import java.lang.Exception

class AudioPlayer(val context: Context) {
	companion object {
		const val PLAYBACK_DEBOUNCE = 4000L
	}
	var timeLastPlayed = 0L
	val timeSinceLastPlayed: Long
		get() = SystemClock.elapsedRealtime() - timeLastPlayed

	private val audioManager = context.getSystemService(AudioManager::class.java)
	private var duckRequest: AudioFocusRequest? = null

	fun playRingtone(uri: Uri?): Boolean {
		if (timeSinceLastPlayed < PLAYBACK_DEBOUNCE) return false
		uri ?: return false
		try {
			val ringtone = RingtoneManager.getRingtone(context, uri) ?: return false
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				ringtone.isLooping = false
			}
			ringtone.audioAttributes = AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_MEDIA)
				.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
				.build()
			ringtone.play()
			timeLastPlayed = SystemClock.elapsedRealtime()
		} catch (e: Exception) {
			Log.e(TAG, "Error while playing notification sound", e)
			return false
		}
		return true
	}

	fun requestDuck() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val duckRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).run {
				setAudioAttributes(AudioAttributes.Builder().run {
					setUsage(AudioAttributes.USAGE_MEDIA)
					setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
					build()
				})
				setAcceptsDelayedFocusGain(false)
				build()
			}
			val success = audioManager.requestAudioFocus(duckRequest)
			if (success == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
//				Log.i(TAG, "Successfully ducked audio $success")
				Thread.sleep(500)   // wait for fadeout before playing notification sound
			} else {
				Log.i(TAG, "Error while ducking audio ($success)")
			}
			this.duckRequest = duckRequest
		} else {
//			Log.i(TAG, "Skipping audio duck on old phone")
		}
	}

	fun releaseDuck() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val duckRequest = this.duckRequest ?: return
			audioManager.abandonAudioFocusRequest(duckRequest)
		}
		this.duckRequest = null
	}
}