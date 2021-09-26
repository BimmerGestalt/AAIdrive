package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.os.Handler
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.CarCapabilitiesSummarized
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.CarInformationObserver
import me.hufman.androidautoidrive.R
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

	private val _isCarConnected = MutableLiveData<Boolean>(false)
	val isCarConnnected: LiveData<Boolean> = _isCarConnected

	private val _isAudioContextSupported = MutableLiveData(false)
	val isAudioContextSupported: LiveData<Boolean> = _isAudioContextSupported
	private val _audioContextStatus = MutableLiveData<Context.() -> String>()
	val audioContextStatus: LiveData<Context.() -> String> = _audioContextStatus
	private val _audioContextHint = MutableLiveData<Context.() -> String>()
	val audioContextHint: LiveData<Context.() -> String> = _audioContextHint

	private val _isAudioStateSupported = MutableLiveData(false)
	val isAudioStateSupported: LiveData<Boolean> = _isAudioStateSupported
	private val _audioStateStatus = MutableLiveData<Context.() -> String>()
	val audioStateStatus: LiveData<Context.() -> String> = _audioStateStatus
	private val _audioStateHint = MutableLiveData<Context.() -> String>()
	val audioStateHint: LiveData<Context.() -> String> = _audioStateHint

	private val _isPopupSupported = MutableLiveData(false)
	val isPopupSupported: LiveData<Boolean> = _isPopupSupported
	private val _isPopupNotSupported = MutableLiveData(false)
	val isPopupNotSupported: LiveData<Boolean> = _isPopupNotSupported
	private val _popupStatus = MutableLiveData<Context.() -> String>()
	val popupStatus: LiveData<Context.() -> String> = _popupStatus
	private val _popupHint = MutableLiveData<Context.() -> String>()
	val popupHint: LiveData<Context.() -> String> = _popupHint

	private val _isTtsSupported = MutableLiveData(false)
	val isTtsSupported: LiveData<Boolean> = _isTtsSupported
	private val _isTtsNotSupported = MutableLiveData(false)
	val isTtsNotSupported: LiveData<Boolean> = _isTtsNotSupported
	private val _ttsStatus = MutableLiveData<Context.() -> String>()
	val ttsStatus: LiveData<Context.() -> String> = _ttsStatus

	private val _isNaviSupported = MutableLiveData(false)
	val isNaviSupported: LiveData<Boolean> = _isNaviSupported
	private val _isNaviNotSupported = MutableLiveData(false)
	val isNaviNotSupported: LiveData<Boolean> = _isNaviNotSupported
	private val _naviStatus = MutableLiveData<Context.() -> String>()
	val naviStatus: LiveData<Context.() -> String> = _naviStatus

	fun update() {
		val summarized = CarCapabilitiesSummarized(carInformation)

		_isCarConnected.value = carInformation.capabilities.isNotEmpty()

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

		_isAudioStateSupported.value = musicAppMode.supportsId5Playback()
		if (musicAppMode.supportsId5Playback()) {
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

		_isPopupSupported.value = summarized.isPopupSupported
		_isPopupNotSupported.value = summarized.isPopupNotSupported
		_popupStatus.value = { getString(R.string.txt_capabilities_popup_yes) }
		_popupHint.value = { "" }

		_isTtsSupported.value = summarized.isTtsSupported
		_isTtsNotSupported.value = summarized.isTtsNotSupported
		if (summarized.isTtsSupported) {
			_ttsStatus.value = { getString(R.string.txt_capabilities_tts_yes) }
		} else {
			_ttsStatus.value = { getString(R.string.txt_capabilities_tts_no) }
		}

		_isNaviSupported.value = summarized.isNaviSupported
		_isNaviNotSupported.value = summarized.isNaviNotSupported
		if (summarized.isNaviSupported) {
			_naviStatus.value = { getString(R.string.txt_capabilities_navi_yes) }
		} else {
			_naviStatus.value = { getString(R.string.txt_capabilities_navi_no) }
		}
	}
}