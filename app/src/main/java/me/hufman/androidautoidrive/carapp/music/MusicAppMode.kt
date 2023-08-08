package me.hufman.androidautoidrive.carapp.music

import android.content.Context
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionObserver
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.AppSettingsViewer
import me.hufman.androidautoidrive.utils.PackageManagerCompat.getPackageInfoCompat

/**
 * Logic to help decide when to use Audio Context and the ID5 layout
 *
 * The only allowed scenarios are:
 *     Bluetooth app connection (the Connected app uses a distinct TCP port for each transport)
 *     USB app connection if the phone has AOAv2 audio support (generally, running an OS earlier than Oreo)
 */
class MusicAppMode(val iDriveConnectionStatus: IDriveConnectionStatus, val capabilities: Map<String, String?>, val appSettings: AppSettings,
                   val isConnectedInstalled: Boolean, val iHeartRadioVersion: String?, val pandoraVersion: String?, val spotifyVersion: String?) {
	companion object {
		fun getIHeartRadioVersion(context: Context): String? {
			return context.packageManager.getPackageInfoCompat("com.pandora.android", 0)?.versionName
		}
		fun getPandoraVersion(context: Context): String? {
			return context.packageManager.getPackageInfoCompat("com.clearchannel.iheartradio.connect", 0)?.versionName ?:
			       context.packageManager.getPackageInfoCompat("com.clearchannel.iheartradio.controller", 0)?.versionName
		}
		fun getSpotifyVersion(context: Context): String? {
			return context.packageManager.getPackageInfoCompat("com.spotify.music", 0)?.versionName
		}

		fun build(capabilities: Map<String, String?>, context: Context): MusicAppMode {
			val isConnectedInstalled = SecurityAccess.installedSecurityServices.any {
				it.name.startsWith("BMWC") || it.name.startsWith("MiniC")
			}
			val iHeartRadioVersion = getIHeartRadioVersion(context)
			val pandoraVersion = getPandoraVersion(context)
			val spotifyVersion = getSpotifyVersion(context)
			return MusicAppMode(IDriveConnectionObserver(), capabilities, AppSettingsViewer(), isConnectedInstalled, iHeartRadioVersion, pandoraVersion, spotifyVersion)
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
		fun toPort(): Int {
			return when (this) {
				USB -> 4004
				BT -> 4007
				ETH -> 4008
			}
		}
	}

	fun isBTConnection(): Boolean {
		return TRANSPORT_PORTS.fromPort(iDriveConnectionStatus.port) == TRANSPORT_PORTS.BT
	}
	fun supportsUsbAudio(): Boolean {
		return appSettings[AppSettings.KEYS.AUDIO_SUPPORTS_USB].toBoolean()
	}
	fun heuristicAudioContext(): Boolean {
		val useUSB = supportsUsbAudio() // works even if an old phone is connected over BT
		val useBT = isBTConnection()
		return useUSB || useBT
	}
	fun shouldRequestAudioContext(): Boolean {
		val manualOverride = appSettings[AppSettings.KEYS.AUDIO_FORCE_CONTEXT].toBoolean()
		if (manualOverride) {
			return manualOverride
		}
		return heuristicAudioContext()
	}
	fun isId4(): Boolean {
		return capabilities["hmi.type"]?.contains("ID4") == true
	}
	fun isNewSpotifyInstalled(): Boolean {
		val spotifySplits = spotifyVersion?.split('.')?.map { it.toIntOrNull() ?: 0 }
		return spotifySplits != null && spotifySplits.size >= 3 && (
				spotifySplits[0] > 8 ||
						(spotifySplits[0] == 8 && spotifySplits[1] > 5) ||
						(spotifySplits[0] == 8 && spotifySplits[1] == 5 && spotifySplits[2] >= 68)
				)
	}
	/** Whether to automatically start Spotify mode, ignoring from any advanced settings */
	fun heuristicAudioState(): Boolean {
		val isSpotifyNotEnabled = spotifyVersion != null && !isConnectedInstalled
		return !isId4() && (isSpotifyNotEnabled || isNewSpotifyInstalled()) && shouldRequestAudioContext()
	}
	/** Whether the current mode starts Spotify mode, including the forced advanced setting */
	fun supportsId5Playback(): Boolean {
		val manualOverride = !isId4() && appSettings[AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT].toBoolean()
		val autodetect = heuristicAudioState()
		return manualOverride || autodetect
	}
	/** Whether to show the Spotify playback view or Audioplayer mode, even if running in Spotify mode */
	fun shouldId5Playback(): Boolean {
		val preferOld = appSettings[AppSettings.KEYS.FORCE_AUDIOPLAYER_LAYOUT].toBoolean()
		return !preferOld && supportsId5Playback()
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