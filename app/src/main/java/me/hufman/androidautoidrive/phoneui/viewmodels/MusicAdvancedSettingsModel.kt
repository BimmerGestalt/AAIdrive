package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BooleanLiveSetting
import me.hufman.androidautoidrive.StringLiveSetting

class MusicAdvancedSettingsModel(appContext: Context): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return MusicAdvancedSettingsModel(appContext) as T
		}
	}

	val showAdvanced = BooleanLiveSetting(appContext, AppSettings.KEYS.SHOW_ADVANCED_SETTINGS)
	val connectionProbeIps = StringLiveSetting(appContext, AppSettings.KEYS.CONNECTION_PROBE_IPS)
	val audioContext = BooleanLiveSetting(appContext, AppSettings.KEYS.AUDIO_FORCE_CONTEXT)
	val spotifyLayout = BooleanLiveSetting(appContext, AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT)
	val audioplayerLayout = BooleanLiveSetting(appContext, AppSettings.KEYS.FORCE_AUDIOPLAYER_LAYOUT)
}