package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.databinding.MusicAdvancedSettingsBinding
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicAdvancedSettingsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.activityViewModels

class MusicAdvancedSettingsFragment: Fragment() {
	val settingsModel by activityViewModels<MusicAdvancedSettingsModel> { MusicAdvancedSettingsModel.Factory(requireContext().applicationContext) }
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val binding = MusicAdvancedSettingsBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.settings = settingsModel
		return binding.root
	}
}