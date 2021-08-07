package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.databinding.NotificationSettingsBinding
import me.hufman.androidautoidrive.phoneui.viewmodels.CarCapabilitiesViewModel
import me.hufman.androidautoidrive.phoneui.viewmodels.NotificationSettingsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.viewModels

class NotificationSettingsFragment: Fragment() {
	val capabilities by viewModels<CarCapabilitiesViewModel> { CarCapabilitiesViewModel.Factory(requireContext().applicationContext) }
	val settingsModel by viewModels<NotificationSettingsModel> { NotificationSettingsModel.Factory(requireContext().applicationContext) }
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val binding = NotificationSettingsBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.capabilities = capabilities
		binding.settings = settingsModel
		return binding.root
	}
}