package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.QueueMetadata
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import me.hufman.androidautoidrive.music.spotify.SpotifyWebApi

class MusicActivityModel(val musicController: MusicController, val spotifyWebApi: SpotifyWebApi): ViewModel() {
	class Factory(val appContext: Context, val musicApp: MusicAppInfo): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel?> create(modelClass: Class<T>): T {
			var model: MusicActivityModel? = null
			val controller = MusicController(appContext, Handler())
			val spotifyWebApi = SpotifyWebApi.getInstance(appContext, MutableAppSettingsReceiver(appContext))
			controller.connectAppManually(musicApp)
			controller.listener = Runnable {
				model?.update()
			}
			model = MusicActivityModel(controller, spotifyWebApi)
			// prepare initial data
			model.update()
			return model as T
		}
	}

	// allow manual callbacks
	private val _redrawListener = MutableLiveData<Long>()
	val redrawListener: LiveData<Long> = _redrawListener

	private val _connected = MutableLiveData<Boolean>(true)
	val connected: LiveData<Boolean> = _connected
	private val _artist = MutableLiveData<String>("")
	val artist: LiveData<String> = _artist
	private val _album = MutableLiveData<String>("")
	val album: LiveData<String> = _album
	private val _title = MutableLiveData<String?>("")
	val title: LiveData<String?> = _title
	private val _coverArt = MutableLiveData<Bitmap?>()
	val coverArt: LiveData<Bitmap?> = _coverArt

	private val _queueMetadata = MutableLiveData<QueueMetadata?>()
	val queueMetadata: LiveData<QueueMetadata?> = _queueMetadata

	private val _isPaused = MutableLiveData<Boolean>(false)
	val isPaused: LiveData<Boolean> = _isPaused
	private val _playbackPosition = MutableLiveData(0)
	val playbackPosition: LiveData<Int> = _playbackPosition
	private val _maxPosition = MutableLiveData(0)
	val maxPosition: LiveData<Int> = _maxPosition

	private val _errorTitle = MutableLiveData<String?>()
	val errorTitle: LiveData<String?> = _errorTitle
	private val _errorMessage = MutableLiveData<String?>()
	val errorMessage: LiveData<String?> = _errorMessage
	private val _isWebApiAuthorized = MutableLiveData<Boolean?>()
	val isWebApiAuthorized: LiveData<Boolean?> = _isWebApiAuthorized

	@VisibleForTesting
	fun update() {
		_connected.value = musicController.isConnected()

		val metadata = musicController.getMetadata()
		_artist.value = metadata?.artist ?: ""
		_album.value = metadata?.album ?: ""
		_title.value = metadata?.title ?: ""
		_coverArt.value = metadata?.coverArt

		_queueMetadata.value = musicController.getQueue()

		val playbackPosition = musicController.getPlaybackPosition()
		_isPaused.value = playbackPosition.isPaused
		_playbackPosition.value = (playbackPosition.getPosition() / 1000).toInt()
		_maxPosition.value = (playbackPosition.maximumPosition / 1000).toInt()

		updateErrors()

		_redrawListener.value = System.currentTimeMillis()
	}

	private fun updateErrors() {
		val spotifyError = musicController.connectors.filterIsInstance<SpotifyAppController.Connector>().firstOrNull()?.lastError
		val isWebApiAuthorized = spotifyWebApi.isAuthorized()
		if (spotifyError != null || (spotifyWebApi.isUsingSpotify && !isWebApiAuthorized)) {
			_errorTitle.value = spotifyError?.javaClass?.simpleName
			_errorMessage.value = spotifyError?.message
			_isWebApiAuthorized.value = isWebApiAuthorized
		} else {
			_errorTitle.value = null
			_errorMessage.value = null
			_isWebApiAuthorized.value = null
		}
	}

	override fun onCleared() {
		super.onCleared()
		musicController.disconnectApp(pause=false)
	}
}