package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.databinding.MusicNowPlayingBinding
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.phoneui.UIState
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicActivityIconsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicActivityModel
import me.hufman.androidautoidrive.phoneui.viewmodels.activityViewModels

class MusicNowPlayingFragment: Fragment() {
	lateinit var musicController: MusicController

	val viewModel by activityViewModels<MusicActivityModel> { MusicActivityModel.Factory(requireContext().applicationContext, UIState.selectedMusicApp) }
	val iconsModel by activityViewModels<MusicActivityIconsModel> { MusicActivityIconsModel.Factory(requireActivity()) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val binding = MusicNowPlayingBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.controller = viewModel.musicController
		binding.viewModel = viewModel
		binding.iconsModel = iconsModel
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		musicController = viewModel.musicController

		// handlers
		view.findViewById<ImageView>(R.id.imgError).setOnClickListener {
			val arguments = Bundle().apply {
				putString(SpotifyApiErrorDialog.EXTRA_TITLE, viewModel.errorTitle.value)
				putString(SpotifyApiErrorDialog.EXTRA_MESSAGE, viewModel.errorMessage.value)
				putBoolean(SpotifyApiErrorDialog.EXTRA_WEB_API_AUTHORIZED, viewModel.isWebApiAuthorized.value == true)
			}
			val fragmentManager = activity?.supportFragmentManager
			if (fragmentManager != null) {
				SpotifyApiErrorDialog().apply {
					setArguments(arguments)
					show(fragmentManager, "spotify_error")
				}
			}
		}
	}
}