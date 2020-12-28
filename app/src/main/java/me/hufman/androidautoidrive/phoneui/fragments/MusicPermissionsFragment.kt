package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import me.hufman.androidautoidrive.databinding.MusicPermissionsBinding
import me.hufman.androidautoidrive.phoneui.controllers.PermissionsController
import me.hufman.androidautoidrive.phoneui.viewmodels.PermissionsModel

class MusicPermissionsFragment: Fragment() {
	val viewModel by viewModels<PermissionsModel> { PermissionsModel.Factory(requireContext().applicationContext) }
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val binding = MusicPermissionsBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.controller = PermissionsController(requireActivity())
		binding.viewModel = viewModel
		return binding.root
	}

	override fun onResume() {
		super.onResume()
		viewModel.update()
		viewModel.updateSpotify()
	}
}