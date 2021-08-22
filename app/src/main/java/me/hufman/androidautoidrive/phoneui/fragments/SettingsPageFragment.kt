package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.databinding.SettingsPageBinding
import me.hufman.androidautoidrive.phoneui.viewmodels.LanguageSettingsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.viewModels

class SettingsPageFragment: Fragment() {
	val viewModel by viewModels<LanguageSettingsModel> { LanguageSettingsModel.Factory(requireContext().applicationContext) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val binding = SettingsPageBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.viewModel = viewModel
		return binding.root
	}
}
