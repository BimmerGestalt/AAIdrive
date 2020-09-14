package me.hufman.androidautoidrive.carapp.music

import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettings
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener

/**
 * Logic to help decide when to use Audio Context
 *
 * The only allowed scenarios are:
 *     Bluetooth app connection (the Connected app uses a distinct TCP port for each transport)
 *     USB app connection if the phone has AOAv2 audio support (generally, running an OS earlier than Oreo)
 */
class MusicAppMode(val appSettings: MutableAppSettings) {
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
}