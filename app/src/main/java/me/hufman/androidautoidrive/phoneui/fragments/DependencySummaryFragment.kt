package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.databinding.DependencySummaryBinding
import me.hufman.androidautoidrive.phoneui.controllers.DependencyInfoController
import me.hufman.androidautoidrive.phoneui.controllers.PermissionsController
import me.hufman.androidautoidrive.phoneui.viewmodels.DependencyInfoModel
import me.hufman.androidautoidrive.phoneui.viewmodels.activityViewModels
import me.hufman.androidautoidrive.phoneui.viewmodels.PermissionsModel

class DependencySummaryFragment: Fragment() {
	val viewModel by activityViewModels<DependencyInfoModel> { DependencyInfoModel.Factory(requireContext().applicationContext) }
	val permissionsModel by activityViewModels<PermissionsModel> { PermissionsModel.Factory(requireContext().applicationContext) }
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val controller = DependencyInfoController(requireContext().applicationContext)
		val permissionsController = PermissionsController(requireActivity())
		val binding = DependencySummaryBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.controller = controller
		binding.viewModel = viewModel
		binding.permissionsModel = permissionsModel
		binding.permissionsController = permissionsController
		return binding.root
	}

	override fun onResume() {
		super.onResume()
		permissionsModel.update()
	}
}