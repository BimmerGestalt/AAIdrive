package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.os.Handler
import android.os.Looper
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
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			val postHandler = Handler(Looper.getMainLooper())
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

	private val _isCarConnected = MutableLiveData(false)
	val isCarConnected: LiveData<Boolean> = _isCarConnected
	private val _isJ29Connected = MutableLiveData(false)
	val isJ29Connected: LiveData<Boolean> = _isJ29Connected

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

		// only these brands of cars support RHMI apps
		// check the cert brand, in case of friendly J29s
		val carBrandSupported = carInformation.connectionBrand?.uppercase() == "BMW" || carInformation.connectionBrand?.uppercase() == "MINI"
		_isCarConnected.value = carInformation.capabilities.isNotEmpty() && carBrandSupported
		_isJ29Connected.value = summarized.isJ29

		_isAudioContextSupported.value = carBrandSupported && musicAppMode.heuristicAudioContext()
		if (carBrandSupported && musicAppMode.heuristicAudioContext()) {
			_audioContextStatus.value = { getString(R.string.txt_capabilities_audiocontext_yes) }
			_audioContextHint.value = { "" }
		} else {
			if (carBrandSupported && !musicAppMode.isBTConnection() && !musicAppMode.supportsUsbAudio()) {
				_audioContextStatus.value = { getString(R.string.txt_capabilities_audiocontext_no_usb) }
				_audioContextHint.value = { getString(R.string.txt_capabilities_audiocontext_hint) }
			} else {
				_audioContextStatus.value = { getString(R.string.txt_capabilities_audiocontext_no) }
				_audioContextHint.value = { "" }
			}
		}

		_isAudioStateSupported.value = carBrandSupported && musicAppMode.supportsId5Playback()
		if (carBrandSupported && musicAppMode.supportsId5Playback()) {
			_audioStateStatus.value = { getString(R.string.txt_capabilities_audiostate_yes) }
			_audioStateHint.value = { "" }
		} else {
			_audioStateStatus.value = { getString(R.string.txt_capabilities_audiostate_no) }
			if (!carBrandSupported) {
				_audioStateHint.value = { "" }      // not supported, but not displayed in the UI
			} else if (musicAppMode.isId4()) {
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
		_popupStatus.value = if (summarized.isPopupSupported) {
			{ getString(R.string.txt_capabilities_popup_yes) }
		} else {
			{ getString(R.string.txt_capabilities_popup_no) }
		}
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