package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import kotlinx.android.synthetic.main.fragment_music_advancedsettings.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BooleanLiveSetting
import me.hufman.androidautoidrive.R

class MusicAdvancedSettingsFragment: Fragment() {
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_music_advancedsettings, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val audioContextSetting = BooleanLiveSetting(requireContext(), AppSettings.KEYS.AUDIO_FORCE_CONTEXT)
		audioContextSetting.observe(this, Observer {
			swAudioContext.isChecked = it
		})
		swAudioContext.setOnCheckedChangeListener { _, isChecked ->
			audioContextSetting.setValue(isChecked)
		}

		val spotifyLayoutSetting = BooleanLiveSetting(requireContext(), AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT)
		spotifyLayoutSetting.observe(this, Observer {
			swSpotifyLayout.isChecked = it
		})
		swSpotifyLayout.setOnCheckedChangeListener { _, isChecked ->
			spotifyLayoutSetting.setValue(isChecked)
		}
	}
}