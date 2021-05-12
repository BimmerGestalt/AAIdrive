package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.fragment_overviewpage.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.databinding.OverviewBinding
import me.hufman.androidautoidrive.phoneui.viewmodels.ConnectionStatusModel
import me.hufman.androidautoidrive.phoneui.viewmodels.activityViewModels

class OverviewPageFragment: Fragment() {
	private val viewModel by activityViewModels<ConnectionStatusModel> { ConnectionStatusModel.Factory(requireContext().applicationContext) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val binding = OverviewBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.viewModel = viewModel
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		paneOverviewConnecting.setOnClickListener {
			findNavController().navigate(R.id.nav_connection)
		}
	}
}