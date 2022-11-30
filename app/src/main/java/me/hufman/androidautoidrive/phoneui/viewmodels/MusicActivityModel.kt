package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.*
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import me.hufman.androidautoidrive.music.spotify.SpotifyWebApi

class MusicActivityModel(val musicApp: MusicAppInfo, val musicController: MusicController, val spotifyWebApi: SpotifyWebApi): ViewModel() {
	class Factory(val appContext: Context, val musicApp: MusicAppInfo?): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel?> create(modelClass: Class<T>): T {
			var model: MusicActivityModel? = null
			val controller = MusicController(appContext, Handler(Looper.getMainLooper()))
			val spotifyWebApi = SpotifyWebApi.getInstance(appContext, MutableAppSettingsReceiver(appContext))
			val finalApp = musicApp ?: loadPreviousApp(controller)
			controller.connectAppManually(finalApp)
			controller.listener = Runnable {
				model?.update()
			}
			model = MusicActivityModel(finalApp, controller, spotifyWebApi)
			// prepare initial data
			model.update()
			return model as T
		}

		fun loadPreviousApp(controller: MusicController): MusicAppInfo {
			// this will almost certainly find a previous app
			// because this function is only used when the MusicPlayerActivity is closed and re-opened
			// and thus when UIState.selectedMusicApp is cleared
			// because it was opened before, the controller will have a desired app saved
			val desiredApp = controller.loadDesiredApp()
			val discovery = MusicAppDiscovery(appContext, Handler(Looper.getMainLooper()))
			discovery.loadInstalledMusicApps()
			return discovery.allApps.first { it.packageName == desiredApp }
		}
	}

	// allow manual callbacks
	private val _redrawListener = MutableLiveData<Long>()
	val redrawListener: LiveData<Long> = _redrawListener

	// app information
	private val _connected = MutableLiveData(true)
	val connected: LiveData<Boolean> = _connected
	private val _appName = MutableLiveData(musicApp.name)
	val appName: LiveData<String> = _appName
	private val _appIcon = MutableLiveData(musicApp.icon)
	val appIcon: LiveData<Drawable> = _appIcon

	// music information
	private val _artist = MutableLiveData("")
	val artist: LiveData<String> = _artist
	private val _album = MutableLiveData("")
	val album: LiveData<String> = _album
	private val _title = MutableLiveData("")
	val title: LiveData<String?> = _title
	private val _coverArt = MutableLiveData<Bitmap?>()
	val coverArt: LiveData<Bitmap?> = _coverArt

	private val _queueMetadata = MutableLiveData<QueueMetadata?>()
	val queueMetadata: LiveData<QueueMetadata?> = _queueMetadata
	private val _queueEmptyText = MutableLiveData<Context.() -> String>{""}
	val queueEmptyText: LiveData<Context.() -> String> = _queueEmptyText

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
		_queueEmptyText.value = if (_queueMetadata.value?.songs?.isEmpty() == true) {
			{ getString(R.string.MUSIC_BROWSE_EMPTY) }
		} else {
			{ "" }
		}

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
		musicController.listener = null
	}
}

/**
 * Summaries of MusicMetadata to be displayed in various MusicPlayer views
 * Notably, includes the MusicActivityIconsModel for browse/search pages
 */
interface MusicPlayerItem {
	val musicMetadata: MusicMetadata    // the MusicMetadata to use for Browse and Play commands, not for display purposes
	val icon: Bitmap?       // a monochrome icon, for the Browse list
	val coverart: Bitmap?   // a full-color coverart
	val title: String?      // first line of text
	val subtitle: String?   // second line of text
}

class MusicPlayerBrowseItem(val musicActivityIconsModel: MusicActivityIconsModel, override val musicMetadata: MusicMetadata): MusicPlayerItem {
	override val icon: Bitmap
		get() = if (musicMetadata.browseable) {
			musicActivityIconsModel.folderIcon
		} else {
			musicActivityIconsModel.songIcon
		}
	override val coverart: Bitmap?
		get() = musicMetadata.coverArt
	override val title: String?
		get() = musicMetadata.title
	override val subtitle: String?
		get() = musicMetadata.subtitle
}

class MusicPlayerQueueItem(val musicActivityModel: MusicActivityModel, override val musicMetadata: MusicMetadata): MusicPlayerItem {
	val nowPlaying: Boolean
		get() = musicMetadata.queueId == musicActivityModel.musicController.getMetadata()?.queueId && !musicActivityModel.musicController.getPlaybackPosition().isPaused
	override val icon: Bitmap?
		get() = null
	override val coverart: Bitmap?
		get() = musicMetadata.coverArt
	override val title: String?
		get() = musicMetadata.title
	override val subtitle: String?
		get() = musicMetadata.artist
}

class MusicPlayerSearchItem(val musicActivityIconsModel: MusicActivityIconsModel, override val musicMetadata: MusicMetadata): MusicPlayerItem {
	override val icon: Bitmap
		get() = if (musicMetadata.browseable) {
			musicActivityIconsModel.folderIcon
		} else {
			musicActivityIconsModel.songIcon
		}
	override val coverart: Bitmap?
		get() = musicMetadata.coverArt
	override val title: String?
		get() = musicMetadata.title
	override val subtitle: String
		get() {
			return if (musicMetadata.subtitle == "Artist" || musicMetadata.subtitle == "Episode") {
				musicMetadata.subtitle
			} else {
				"${musicMetadata.subtitle} - ${musicMetadata.artist}"
			}
		}
}