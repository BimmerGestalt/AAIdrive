package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.databinding.DependencyInfoBinding
import me.hufman.androidautoidrive.phoneui.controllers.DependencyInfoController
import me.hufman.androidautoidrive.phoneui.viewmodels.DependencyInfoModel

class DependencyInfoFragment: Fragment() {
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val controller = DependencyInfoController(requireContext().applicationContext)
		val viewModel = ViewModelProvider(this, DependencyInfoModel.Factory(requireContext().applicationContext)).get(DependencyInfoModel::class.java)
		val binding = DependencyInfoBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.controller = controller
		binding.viewModel = viewModel
		return binding.root
	}
}