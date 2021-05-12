package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.databinding.DependencyInfoBinding
import me.hufman.androidautoidrive.phoneui.controllers.DependencyInfoController
import me.hufman.androidautoidrive.phoneui.viewmodels.DependencyInfoModel
import me.hufman.androidautoidrive.phoneui.viewmodels.activityViewModels

class DependencyInfoFragment: Fragment() {
	val viewModel by activityViewModels<DependencyInfoModel> { DependencyInfoModel.Factory(requireContext().applicationContext) }
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val controller = DependencyInfoController(requireContext().applicationContext)
		val binding = DependencyInfoBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.controller = controller
		binding.viewModel = viewModel
		return binding.root
	}
}