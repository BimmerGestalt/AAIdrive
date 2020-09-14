package me.hufman.androidautoidrive.carapp.music

import android.content.Context
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettings
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import java.lang.Exception

/**
 * Logic to help decide when to use Audio Context and the ID5 layout
 *
 * The only allowed scenarios are:
 *     Bluetooth app connection (the Connected app uses a distinct TCP port for each transport)
 *     USB app connection if the phone has AOAv2 audio support (generally, running an OS earlier than Oreo)
 */
class MusicAppMode(val capabilities: Map<String, String?>, val appSettings: MutableAppSettings, val spotifyVersion: String?) {
	companion object {
		fun build(capabilities: Map<String, String?>, context: Context): MusicAppMode {
			val spotifyVersion = try {
				context.packageManager.getPackageInfo("com.spotify.music", 0).versionName
			} catch (e: Exception) { null }
			return MusicAppMode(capabilities, MutableAppSettings(context), spotifyVersion)
		}
	}

	/**
	 * Ports for each transport, as identified from the Connected app
	 */
	enum class TRANSPORT_PORTS {
		USB,
		BT,
		ETH;

		companion object {
			fun fromPort(port: Int?): TRANSPORT_PORTS? {
				return when (port) {
					4004 -> USB
					4007 -> BT
					4008 -> ETH
					else -> null
				}
			}
		}
	}

	fun isBTConnection(): Boolean {
		return TRANSPORT_PORTS.fromPort(IDriveConnectionListener.port) == TRANSPORT_PORTS.BT
	}
	fun supportsUsbAudio(): Boolean {
		return appSettings[AppSettings.KEYS.AUDIO_SUPPORTS_USB].toBoolean()
	}
	fun shouldRequestAudioContext(): Boolean {
		val manualOverride = appSettings[AppSettings.KEYS.AUDIO_FORCE_CONTEXT].toBoolean()
		if (manualOverride) {
			return manualOverride
		}
		val useUSB = supportsUsbAudio() // works even if an old phone is connected over BT
		val useBT = isBTConnection()
		return useUSB || useBT
	}
	fun shouldId5Playback(): Boolean {
		val idrive4 = capabilities["hmi.type"]?.contains("ID4") == true
		val manualOverride = !idrive4 && appSettings[AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT].toBoolean()
		val spotifySplits = spotifyVersion?.split('.')?.map { it.toInt() }
		val newSpotifyInstalled = spotifySplits != null && spotifySplits.size >= 3 && (
				spotifySplits[0] > 8 ||
				(spotifySplits[0] == 8 && spotifySplits[1] > 5) ||
				(spotifySplits[0] == 8 && spotifySplits[1] == 5 && spotifySplits[2] >= 68)
		)
		val autodetect = !idrive4 && newSpotifyInstalled && shouldRequestAudioContext()
		return manualOverride || autodetect
	}
}