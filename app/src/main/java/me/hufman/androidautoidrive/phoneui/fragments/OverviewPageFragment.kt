package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.databinding.OverviewBinding
import me.hufman.androidautoidrive.phoneui.controllers.OverviewPageController
import me.hufman.androidautoidrive.phoneui.viewmodels.ConnectionStatusModel
import me.hufman.androidautoidrive.phoneui.viewmodels.activityViewModels

class OverviewPageFragment: Fragment() {
	private val viewModel by activityViewModels<ConnectionStatusModel> { ConnectionStatusModel.Factory(requireContext().applicationContext) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val binding = OverviewBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.viewModel = viewModel
		binding.controller = OverviewPageController(this)
		return binding.root
	}
}