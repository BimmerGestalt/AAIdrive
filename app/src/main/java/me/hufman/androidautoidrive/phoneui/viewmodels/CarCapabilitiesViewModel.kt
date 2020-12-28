package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.os.Handler
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.music.MusicAppMode

class CarCapabilitiesViewModel(val carInformation: CarInformation, val musicAppMode: MusicAppMode): ViewModel() {
	class Factory(val context: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel?> create(modelClass: Class<T>): T {
			val postHandler = Handler()
			var viewModel: CarCapabilitiesViewModel? = null
			val carCapabilities = HashMap<String, String>()
			val musicAppMode = MusicAppMode.build(carCapabilities, context)
			val carInformationObserver = CarInformationObserver { capabilities ->
				postHandler.post {
					carCapabilities.clear()
					carCapabilities.putAll(capabilities)
					viewModel?.update()
				}
			}
			viewModel = CarCapabilitiesViewModel(carInformationObserver, musicAppMode)
			viewModel.update()
			return viewModel as T
		}
	}

	private val _isCarConnected = MutableLiveData<Boolean>()
	val isCarConnnected = _isCarConnected as LiveData<Boolean>

	private val _isAudioContextSupported = MutableLiveData<Boolean>()
	val isAudioContextSupported = _isAudioContextSupported as LiveData<Boolean>
	private val _audioContextStatus = MutableLiveData<Context.() -> String>()
	val audioContextStatus = _audioContextStatus as LiveData<Context.() -> String>
	private val _audioContextHint = MutableLiveData<Context.() -> String>()
	val audioContextHint = _audioContextHint as LiveData<Context.() -> String>

	private val _isAudioStateSupported = MutableLiveData<Boolean>()
	val isAudioStateSupported = _isAudioStateSupported as LiveData<Boolean>
	private val _audioStateStatus = MutableLiveData<Context.() -> String>()
	val audioStateStatus = _audioStateStatus as LiveData<Context.() -> String>
	private val _audioStateHint = MutableLiveData<Context.() -> String>()
	val audioStateHint = _audioStateHint as LiveData<Context.() -> String>

	private val _isPopupSupported = MutableLiveData<Boolean>()
	val isPopupSupported = _isPopupSupported as LiveData<Boolean>
	private val _popupStatus = MutableLiveData<Context.() -> String>()
	val popupStatus = _popupStatus as LiveData<Context.() -> String>
	private val _popupHint = MutableLiveData<Context.() -> String>()
	val popupHint = _popupHint as LiveData<Context.() -> String>

	private val _isTtsSupported = MutableLiveData<Boolean>()
	val isTtsSupported = _isTtsSupported as LiveData<Boolean>
	private val _ttsStatus = MutableLiveData<Context.() -> String>()
	val ttsStatus = _ttsStatus as LiveData<Context.() -> String>

	private val _isNaviSupported = MutableLiveData<Boolean>()
	val isNaviSupported = _isNaviSupported as LiveData<Boolean>
	private val _naviStatus = MutableLiveData<Context.() -> String>()
	val naviStatus = _naviStatus as LiveData<Context.() -> String>

	fun update() {
		val capabilities = carInformation.capabilities

		_isCarConnected.value = capabilities.isNotEmpty()

		_isAudioContextSupported.value = musicAppMode.heuristicAudioContext()
		if (musicAppMode.heuristicAudioContext()) {
			_audioContextStatus.value = { getString(R.string.txt_capabilities_audiocontext_yes) }
			_audioContextHint.value = { "" }
		} else {
			if (!musicAppMode.isBTConnection() && !musicAppMode.supportsUsbAudio()) {
				_audioContextStatus.value = { getString(R.string.txt_capabilities_audiocontext_no_usb) }
			} else {
				_audioContextStatus.value = { getString(R.string.txt_capabilities_audiocontext_no) }
			}
			_audioContextHint.value = { getString(R.string.txt_capabilities_audiocontext_hint) }
		}

		_isAudioStateSupported.value = musicAppMode.shouldId5Playback()
		if (musicAppMode.shouldId5Playback()) {
			_audioStateStatus.value = { getString(R.string.txt_capabilities_audiostate_yes) }
			_audioStateHint.value = { "" }
		} else {
			_audioStateStatus.value = { getString(R.string.txt_capabilities_audiostate_no) }
			if (musicAppMode.isId4()) {
				_audioStateHint.value = { getString(R.string.txt_capabilities_audiostate_id4) }
			} else if (!musicAppMode.shouldRequestAudioContext()) {
				_audioStateHint.value = { getString(R.string.txt_capabilities_audiostate_audiocontext) }
			} else if (!musicAppMode.isNewSpotifyInstalled()) {
				_audioStateHint.value = { getString(R.string.txt_capabilities_audiostate_spotify) }
			} else {
				// unknown reason
				_audioStateHint.value = { "" }
			}
		}

		_isPopupSupported.value = musicAppMode.isId4()
		if (musicAppMode.isId4()) {
			_popupStatus.value = { getString(R.string.txt_capabilities_popup_yes) }
			_popupHint.value = { "" }
		} else {
			_popupStatus.value = { getString(R.string.txt_capabilities_popup_no) }
			_popupHint.value = { getString(R.string.txt_capabilities_popup_hint) }
		}

		_isTtsSupported.value = capabilities["tts"]?.toLowerCase() == "true"
		if (capabilities["tts"]?.toLowerCase() == "true") {
			_ttsStatus.value = { getString(R.string.txt_capabilities_tts_yes) }
		} else {
			_ttsStatus.value = { getString(R.string.txt_capabilities_tts_no) }
		}

		_isNaviSupported.value = capabilities["navi"]?.toLowerCase() == "true"
		if (capabilities["navi"]?.toLowerCase() == "true") {
			_naviStatus.value = { getString(R.string.txt_capabilities_navi_yes) }
		} else {
			_naviStatus.value = { getString(R.string.txt_capabilities_navi_no) }
		}
	}
}