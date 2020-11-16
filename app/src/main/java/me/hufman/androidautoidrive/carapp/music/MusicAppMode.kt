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
class MusicAppMode(val capabilities: Map<String, String?>, val appSettings: MutableAppSettings,
                   val iHeartRadioVersion: String?, val pandoraVersion: String?, val spotifyVersion: String?) {
	companion object {
		fun build(capabilities: Map<String, String?>, context: Context): MusicAppMode {
			val iHeartRadioVersion = try {
				context.packageManager.getPackageInfo("com.pandora.android", 0).versionName
			} catch (e: Exception) { null }
			val pandoraVersion = try {
				context.packageManager.getPackageInfo("com.clearchannel.iheartradio.connect", 0).versionName
			} catch (e: Exception) {
				try {
					context.packageManager.getPackageInfo("com.clearchannel.iheartradio.controller", 0).versionName
				} catch (e: Exception) { null }
			}
			val spotifyVersion = try {
				context.packageManager.getPackageInfo("com.spotify.music", 0).versionName
			} catch (e: Exception) { null }
			return MusicAppMode(capabilities, MutableAppSettings(context), iHeartRadioVersion, pandoraVersion, spotifyVersion)
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
	fun isId4(): Boolean {
		return capabilities["hmi.type"]?.contains("ID4") == true
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

	/** If a single official Radio app is running, return that name */
	fun getRadioAppName(): String? {
		if (!isBTConnection()) return null  // no official radio apps run over USB

		return if (iHeartRadioVersion != null && pandoraVersion == null) {
			"iHeartRadio"
		} else if (iHeartRadioVersion == null && pandoraVersion != null) {
			"Pandora"
		} else null
	}
}