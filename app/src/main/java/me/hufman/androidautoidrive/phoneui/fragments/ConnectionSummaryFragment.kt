package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.databinding.ConnectionSummaryBinding
import me.hufman.androidautoidrive.phoneui.viewmodels.ConnectionStatusModel

class ConnectionSummaryFragment: Fragment() {
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val viewModel = ViewModelProvider(this, ConnectionStatusModel.Factory(requireContext().applicationContext)).get(ConnectionStatusModel::class.java)
		val binding = ConnectionSummaryBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.viewModel = viewModel
		return binding.root
	}
}