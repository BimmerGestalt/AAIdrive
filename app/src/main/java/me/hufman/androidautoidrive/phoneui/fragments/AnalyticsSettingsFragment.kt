package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.databinding.AnalyticsSettingsBinding
import me.hufman.androidautoidrive.phoneui.viewmodels.AnalyticsSettingsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.viewModels

class AnalyticsSettingsFragment: Fragment() {
	val viewModel by viewModels<AnalyticsSettingsModel> { AnalyticsSettingsModel.Factory(requireContext().applicationContext) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val binding = AnalyticsSettingsBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.viewModel = viewModel
		return binding.root
	}
}