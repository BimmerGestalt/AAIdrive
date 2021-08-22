package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.databinding.ConnectionSummaryBinding
import me.hufman.androidautoidrive.phoneui.viewmodels.ConnectionStatusModel
import me.hufman.androidautoidrive.phoneui.viewmodels.activityViewModels

class ConnectionSummaryFragment: Fragment() {
	private val viewModel by activityViewModels<ConnectionStatusModel> { ConnectionStatusModel.Factory(requireContext().applicationContext) }
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val binding = ConnectionSummaryBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.viewModel = viewModel
		return binding.root
	}
}