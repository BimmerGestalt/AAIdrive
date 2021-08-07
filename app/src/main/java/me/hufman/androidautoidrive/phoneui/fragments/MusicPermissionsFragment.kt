package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.databinding.MusicPermissionsBinding
import me.hufman.androidautoidrive.phoneui.controllers.PermissionsController
import me.hufman.androidautoidrive.phoneui.viewmodels.PermissionsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.viewModels

class MusicPermissionsFragment: Fragment() {
	val appSettings by lazy { MutableAppSettingsReceiver(requireContext()) }
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

		// while the screen is open, watch for the controller to clear Spotify Web access
		appSettings.callback = { viewModel._updateSpotifyWeb() }
	}

	override fun onPause() {
		super.onPause()
		appSettings.callback = null
	}
}