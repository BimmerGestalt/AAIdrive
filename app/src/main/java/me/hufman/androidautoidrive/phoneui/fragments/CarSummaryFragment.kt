package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import me.hufman.androidautoidrive.databinding.CarSummaryBinding
import me.hufman.androidautoidrive.phoneui.viewmodels.CarSummaryModel

class CarSummaryFragment: Fragment() {
	val viewModel by activityViewModels<CarSummaryModel> { CarSummaryModel.Factory(requireContext().applicationContext) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val binding = CarSummaryBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.viewModel = viewModel
		return binding.root
	}
}