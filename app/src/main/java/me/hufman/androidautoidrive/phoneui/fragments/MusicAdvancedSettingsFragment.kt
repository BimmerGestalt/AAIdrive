package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_music_advancedsettings.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.R

class MusicAdvancedSettingsFragment: Fragment() {
	val appSettings by lazy { MutableAppSettingsReceiver(requireContext()) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_music_advancedsettings, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		swAudioContext.setOnCheckedChangeListener { _, isChecked ->
			appSettings[AppSettings.KEYS.AUDIO_FORCE_CONTEXT] = isChecked.toString()
		}
		swSpotifyLayout.setOnCheckedChangeListener { _, isChecked ->
			appSettings[AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT] = isChecked.toString()
		}
		redraw()
	}

	fun redraw() {
		swAudioContext.isChecked = appSettings[AppSettings.KEYS.AUDIO_FORCE_CONTEXT].toBoolean()
		swSpotifyLayout.isChecked = appSettings[AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT].toBoolean()
	}
}