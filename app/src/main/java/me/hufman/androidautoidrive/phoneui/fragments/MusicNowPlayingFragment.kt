package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import kotlinx.android.synthetic.main.music_nowplaying.*
import me.hufman.androidautoidrive.databinding.MusicNowPlayingBinding
import me.hufman.androidautoidrive.utils.Utils
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.phoneui.*
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicActivityIconsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicActivityModel

class MusicNowPlayingFragment: Fragment() {
	lateinit var viewModel: MusicActivityModel
	lateinit var musicController: MusicController

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val viewModel = ViewModelProvider(requireActivity()).get(MusicActivityModel::class.java)
		val iconsModel = ViewModelProvider(requireActivity()).get(MusicActivityIconsModel::class.java)

		val binding = MusicNowPlayingBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.controller = viewModel.musicController
		binding.viewModel = viewModel
		binding.iconsModel = iconsModel
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		viewModel = ViewModelProvider(requireActivity()).get(MusicActivityModel::class.java)
		musicController = viewModel.musicController

		// tint the icons for the theme
		imgArtist.colorFilter = Utils.getIconMask(context!!.getThemeColor(android.R.attr.textColorSecondary))
		imgAlbum.colorFilter = Utils.getIconMask(context!!.getThemeColor(android.R.attr.textColorSecondary))
		imgSong.colorFilter = Utils.getIconMask(context!!.getThemeColor(android.R.attr.textColorSecondary))

		// handlers
		imgError.setOnClickListener {
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