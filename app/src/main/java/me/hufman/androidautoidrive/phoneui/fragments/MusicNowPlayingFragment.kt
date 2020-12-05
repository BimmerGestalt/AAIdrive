package me.hufman.androidautoidrive.phoneui.fragments

import android.graphics.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import kotlinx.android.synthetic.main.music_nowplaying.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.Utils
import me.hufman.androidautoidrive.getThemeColor
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.spotify.SpotifyWebApi
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import me.hufman.androidautoidrive.phoneui.MusicActivityModel
import me.hufman.androidautoidrive.phoneui.visible

class MusicNowPlayingFragment: Fragment() {
	companion object {
		const val TAG = "MusicNowPlayingFragment"
		const val ARTIST_ID = "150.png"
		const val ALBUM_ID = "148.png"
		const val SONG_ID = "152.png"
		const val PLACEHOLDER_ID = "147.png"
	}

	lateinit var musicController: MusicController
	lateinit var placeholderCoverArt: Bitmap
	lateinit var spotifyWebApi: SpotifyWebApi

	fun onActive() {
		musicController.listener = Runnable { redraw() }
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.music_nowplaying, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		val viewModel = ViewModelProvider(requireActivity()).get(MusicActivityModel::class.java)
		val musicController = viewModel.musicController ?: return
		this.musicController = musicController
		this.spotifyWebApi = SpotifyWebApi.getInstance(context!!)

		imgArtist.setImageBitmap(viewModel.icons[ARTIST_ID])
		imgAlbum.setImageBitmap(viewModel.icons[ALBUM_ID])
		imgSong.setImageBitmap(viewModel.icons[SONG_ID])
		imgArtist.colorFilter = Utils.getIconMask(context!!.getThemeColor(android.R.attr.textColorSecondary))
		imgAlbum.colorFilter = Utils.getIconMask(context!!.getThemeColor(android.R.attr.textColorSecondary))
		imgSong.colorFilter = Utils.getIconMask(context!!.getThemeColor(android.R.attr.textColorSecondary))
		placeholderCoverArt = viewModel.icons[PLACEHOLDER_ID]!!

		btnPrevious.setOnClickListener { musicController.skipToPrevious() }
		btnPlay.setOnClickListener {
			if (musicController.getPlaybackPosition().playbackPaused) {
				musicController.play()
			} else {
				musicController.pause()
			}
		}
		btnNext.setOnClickListener { musicController.skipToNext() }
		musicController.listener = Runnable { redraw() }

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
	}

	override fun onResume() {
		super.onResume()
		redraw()
	}

	fun redraw() {
		if (!isVisible) return

		val metadata = musicController.getMetadata()
		if (metadata?.coverArt != null) {
			imgCoverArt.setImageBitmap(metadata.coverArt)
		} else {
			imgCoverArt.setImageBitmap(placeholderCoverArt)
		}
		txtArtist.text = if (musicController.isConnected()) { metadata?.artist ?: "" } else { getString(R.string.nowplaying_notconnected) }
		txtAlbum.text = metadata?.album
		txtSong.text = metadata?.title ?: getString(R.string.nowplaying_unknown)

		if (musicController.getPlaybackPosition().playbackPaused) {
			btnPlay.setImageResource(android.R.drawable.ic_media_play)
		} else {
			btnPlay.setImageResource(android.R.drawable.ic_media_pause)
		}
		val position = musicController.getPlaybackPosition()
		seekProgress.progress = (position.getPosition() / 1000).toInt()
		seekProgress.max = (position.maximumPosition / 1000).toInt()

		// show any Spotify errors
		val fragmentManager = activity?.supportFragmentManager
		val spotifyError = musicController.connectors.filterIsInstance<SpotifyAppController.Connector>().firstOrNull()?.lastError
		val isWebApiAuthorized = spotifyWebApi.isAuthorized()

		if (fragmentManager != null && (spotifyError != null || (spotifyWebApi.isUsingSpotify && !isWebApiAuthorized))) {
			imgError.visible = true
			imgError.setOnClickListener {
				val arguments = Bundle().apply {
					putString(SpotifyApiErrorDialog.EXTRA_CLASSNAME, spotifyError?.javaClass?.simpleName)
					putString(SpotifyApiErrorDialog.EXTRA_MESSAGE, spotifyError?.message)
					putBoolean(SpotifyApiErrorDialog.EXTRA_WEB_API_AUTHORIZED, isWebApiAuthorized)
				}
				SpotifyApiErrorDialog().apply {
					setArguments(arguments)
					show(fragmentManager, "spotify_error")
				}
			}
		} else {
			imgError.visible = false
			imgError.setOnClickListener(null)
		}
	}
}