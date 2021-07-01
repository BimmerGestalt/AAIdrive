package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import me.hufman.androidautoidrive.carapp.navigation.AndroidGeocoderSearcher
import me.hufman.androidautoidrive.carapp.navigation.NavigationParser
import me.hufman.androidautoidrive.carapp.navigation.NavigationTriggerSender
import me.hufman.androidautoidrive.carapp.navigation.URLRedirector
import me.hufman.androidautoidrive.databinding.NavigationStatusBindingImpl
import me.hufman.androidautoidrive.phoneui.controllers.NavigationSearchController
import me.hufman.androidautoidrive.phoneui.viewmodels.NavigationStatusModel

class NavigationPageFragment: Fragment() {

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val viewModel by viewModels<NavigationStatusModel> { NavigationStatusModel.Factory(requireContext().applicationContext) }
		val navParser = NavigationParser(AndroidGeocoderSearcher(requireContext().applicationContext), URLRedirector())
		val navTrigger = NavigationTriggerSender(requireContext().applicationContext)
		val controller = NavigationSearchController(lifecycleScope, navParser, navTrigger, viewModel)
		val binding = NavigationStatusBindingImpl.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.viewModel = viewModel
		binding.controller = controller
		return binding.root
	}
}