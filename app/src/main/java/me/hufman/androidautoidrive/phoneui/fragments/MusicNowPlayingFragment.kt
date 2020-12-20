package me.hufman.androidautoidrive.phoneui.fragments

import android.graphics.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import kotlinx.android.synthetic.main.music_nowplaying.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.utils.Utils
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.phoneui.*
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicActivityIconsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicActivityModel

class MusicNowPlayingFragment: Fragment() {
	lateinit var viewModel: MusicActivityModel
	lateinit var musicController: MusicController
	lateinit var placeholderCoverArt: Bitmap

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.music_nowplaying, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		viewModel = ViewModelProvider(requireActivity()).get(MusicActivityModel::class.java)
		musicController = viewModel.musicController

		// subscribe to ViewModel
		viewModel.connected.observe(viewLifecycleOwner, Observer {
			showEither(txtNotConnected, txtArtist) { it }
		})
		viewModel.artist.observe(viewLifecycleOwner, Observer {
			txtArtist.text = it
		})
		viewModel.album.observe(viewLifecycleOwner, Observer {
			txtAlbum.text = it
		})
		viewModel.title.observe(viewLifecycleOwner, Observer {
			txtSong.text = it ?: getString(R.string.nowplaying_unknown)
		})
		viewModel.coverArt.observe(viewLifecycleOwner, Observer {
			imgCoverArt.setImageBitmap(it ?: placeholderCoverArt)
		})

		viewModel.isPaused.observe(viewLifecycleOwner, Observer {
			btnPlay.setImageResource(if (it == true) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
		})

		viewModel.playbackPosition.observe(viewLifecycleOwner, Observer {
			seekProgress.progress = it
		})
		viewModel.maxPosition.observe(viewLifecycleOwner, Observer {
			seekProgress.max = it
		})

		viewModel.errorTitle.observe(viewLifecycleOwner, Observer {
			imgError.visible = it != null
		})

		// load the icons
		val iconModel = ViewModelProvider(requireActivity()).get(MusicActivityIconsModel::class.java)
		initializeIcons(iconModel)

		// handlers
		btnPrevious.setOnClickListener { musicController.skipToPrevious() }
		btnPlay.setOnClickListener {
			if (musicController.getPlaybackPosition().playbackPaused) {
				musicController.play()
			} else {
				musicController.pause()
			}
		}
		btnNext.setOnClickListener { musicController.skipToNext() }

		seekProgress.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
			override fun onProgressChanged(seekbar: SeekBar?, value: Int, fromUser: Boolean) {
				if (fromUser) {
					musicController.seekTo(value * 1000L)
				}
			}

			override fun onStartTrackingTouch(p0: SeekBar?) {
			}
			override fun onStopTrackingTouch(p0: SeekBar?) {
			}
		})

		imgError.setOnClickListener {
			val arguments = Bundle().apply {
				putString(SpotifyApiErrorDialog.EXTRA_CLASSNAME, viewModel.errorTitle.value)
				putString(SpotifyApiErrorDialog.EXTRA_MESSAGE, viewModel.errorMessage.value)
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

	fun initializeIcons(iconsModel: MusicActivityIconsModel) {
		imgArtist.setImageBitmap(iconsModel.artistIcon)
		imgAlbum.setImageBitmap(iconsModel.albumIcon)
		imgSong.setImageBitmap(iconsModel.songIcon)
		imgArtist.colorFilter = Utils.getIconMask(context!!.getThemeColor(android.R.attr.textColorSecondary))
		imgAlbum.colorFilter = Utils.getIconMask(context!!.getThemeColor(android.R.attr.textColorSecondary))
		imgSong.colorFilter = Utils.getIconMask(context!!.getThemeColor(android.R.attr.textColorSecondary))
		placeholderCoverArt = iconsModel.placeholderCoverArt
	}
}