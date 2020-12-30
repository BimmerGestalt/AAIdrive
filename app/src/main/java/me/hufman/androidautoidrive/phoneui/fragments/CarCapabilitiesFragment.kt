package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import me.hufman.androidautoidrive.databinding.CarCapabilitiesBinding
import me.hufman.androidautoidrive.phoneui.viewmodels.CarCapabilitiesViewModel

class CarCapabilitiesFragment: Fragment() {
	private val viewModel by viewModels<CarCapabilitiesViewModel> { CarCapabilitiesViewModel.Factory(requireContext().applicationContext) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val viewModel by viewModels<CarCapabilitiesViewModel> { CarCapabilitiesViewModel.Factory(requireContext().applicationContext) }
		val binding = CarCapabilitiesBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.viewModel = viewModel
		return binding.root
	}
}