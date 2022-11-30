package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.databinding.MapPageSettingsBinding
import me.hufman.androidautoidrive.phoneui.viewmodels.ConnectionStatusModel
import me.hufman.androidautoidrive.phoneui.viewmodels.MapPageModel
import me.hufman.androidautoidrive.phoneui.viewmodels.activityViewModels
import me.hufman.androidautoidrive.phoneui.viewmodels.viewModels

class MapsPageFragment: Fragment() {
	private val mapPageModel by viewModels<MapPageModel> { MapPageModel.Factory(requireContext().applicationContext) }
	private val connectionViewModel by activityViewModels<ConnectionStatusModel> { ConnectionStatusModel.Factory(requireContext().applicationContext) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val binding = MapPageSettingsBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.connectionmodel = connectionViewModel
		binding.viewmodel = mapPageModel
		return binding.root
	}
}