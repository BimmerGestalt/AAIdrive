package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import me.hufman.androidautoidrive.databinding.CarDrivingStatsBinding
import me.hufman.androidautoidrive.phoneui.controllers.SendIntentController
import me.hufman.androidautoidrive.phoneui.viewmodels.CarDrivingStatsModel

class CarDrivingStatsFragment: Fragment() {
	val controller by lazy { SendIntentController(this.requireContext().applicationContext) }
	val viewModel by activityViewModels<CarDrivingStatsModel> { CarDrivingStatsModel.Factory(requireContext().applicationContext) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val binding = CarDrivingStatsBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.controller = controller
		binding.viewModel = viewModel
		return binding.root
	}
}