package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.QueueMetadata
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController

class MusicActivityModel(val musicController: MusicController): ViewModel() {
	class Factory(val appContext: Context, val musicApp: MusicAppInfo): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel?> create(modelClass: Class<T>): T {
			var model: MusicActivityModel? = null
			val controller = MusicController(appContext, Handler())
			controller.connectAppManually(musicApp)
			controller.listener = Runnable {
				model?.update()
			}
			model = MusicActivityModel(controller)
			// prepare initial data
			model.update()
			return model as T
		}
	}

	// allow manual callbacks
	private val _redrawListener = MutableLiveData<Long>()
	val redrawListener = _redrawListener as LiveData<Long>

	private val _connected = MutableLiveData<Boolean>(true)
	val connected = _connected as LiveData<Boolean>
	private val _artist = MutableLiveData<String>("")
	val artist = _artist as LiveData<String>
	private val _album = MutableLiveData<String>("")
	val album = _album as LiveData<String>
	private val _title = MutableLiveData<String?>("")
	val title = _title as LiveData<String?>
	private val _coverArt = MutableLiveData<Bitmap?>()
	val coverArt = _coverArt as LiveData<Bitmap?>

	private val _queueMetadata = MutableLiveData<QueueMetadata?>()
	val queueMetadata = _queueMetadata as LiveData<QueueMetadata?>

	private val _isPaused = MutableLiveData<Boolean>(false)
	val isPaused = _isPaused as LiveData<Boolean>
	private val _playbackPosition = MutableLiveData(0)
	val playbackPosition = _playbackPosition as LiveData<Int>
	private val _maxPosition = MutableLiveData(0)
	val maxPosition = _maxPosition as LiveData<Int>

	private val _errorTitle = MutableLiveData<String?>()
	val errorTitle = _errorTitle as LiveData<String?>
	private val _errorMessage = MutableLiveData<String?>()
	val errorMessage = _errorMessage as LiveData<String?>

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
		_isPaused.value = playbackPosition.playbackPaused
		_playbackPosition.value = (playbackPosition.getPosition() / 1000).toInt()
		_maxPosition.value = (playbackPosition.maximumPosition / 1000).toInt()

		updateErrors()

		_redrawListener.value = System.currentTimeMillis()
	}

	private fun updateErrors() {
		val spotifyError = musicController.connectors.filterIsInstance<SpotifyAppController.Connector>().firstOrNull()?.lastError
		if (spotifyError != null) {
			_errorTitle.value = spotifyError.javaClass.simpleName
			_errorMessage.value = spotifyError.message
		} else {
			_errorTitle.value = null
			_errorMessage.value = null
		}
	}

	override fun onCleared() {
		super.onCleared()
		musicController.disconnectApp(pause=false)
	}
}