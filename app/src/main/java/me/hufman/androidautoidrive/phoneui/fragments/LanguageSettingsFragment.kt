package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.databinding.LanguageSettingsBinding
import me.hufman.androidautoidrive.phoneui.viewmodels.LanguageSettingsModel

class LanguageSettingsFragment: Fragment() {
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val viewModel = ViewModelProvider(this, LanguageSettingsModel.Factory(requireContext().applicationContext)).get(LanguageSettingsModel::class.java)
		val binding = LanguageSettingsBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.viewModel = viewModel
		return binding.root
	}
}