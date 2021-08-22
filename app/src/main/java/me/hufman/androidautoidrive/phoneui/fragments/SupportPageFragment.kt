package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.databinding.SupportPageBinding
import me.hufman.androidautoidrive.phoneui.controllers.SupportPageController
import me.hufman.androidautoidrive.phoneui.viewmodels.SupportPageModel
import me.hufman.androidautoidrive.phoneui.viewmodels.viewModels

class SupportPageFragment: Fragment() {
	val viewModel by viewModels<SupportPageModel>()
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val binding = SupportPageBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.viewModel = viewModel
		binding.controller = SupportPageController()
		return binding.root
	}
}