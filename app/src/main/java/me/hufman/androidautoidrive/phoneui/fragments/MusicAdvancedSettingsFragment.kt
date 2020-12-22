package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.databinding.MusicAdvancedSettingsBinding
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicAdvancedSettingsModel

class MusicAdvancedSettingsFragment: Fragment() {
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val settingsModel = ViewModelProvider(this, MusicAdvancedSettingsModel.Factory(requireContext().applicationContext)).get(MusicAdvancedSettingsModel::class.java)
		val binding = MusicAdvancedSettingsBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.settings = settingsModel
		return binding.root
	}
}