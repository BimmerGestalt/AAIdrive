package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.databinding.NotificationSettingsBinding
import me.hufman.androidautoidrive.phoneui.viewmodels.CarCapabilitiesViewModel
import me.hufman.androidautoidrive.phoneui.viewmodels.NotificationSettingsModel

class NotificationSettingsFragment: Fragment() {

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val capabilities by viewModels<CarCapabilitiesViewModel> { CarCapabilitiesViewModel.Factory(requireContext().applicationContext) }
		val settingsModel = ViewModelProvider(this, NotificationSettingsModel.Factory(requireContext().applicationContext)).get(NotificationSettingsModel::class.java)
		val binding = NotificationSettingsBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.capabilities = capabilities
		binding.settings = settingsModel
		return binding.root
	}
}