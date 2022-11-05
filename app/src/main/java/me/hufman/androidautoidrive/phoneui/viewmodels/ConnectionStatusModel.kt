package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.CarInformationObserver
import me.hufman.androidautoidrive.ChassisCode
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.connections.CarConnectionDebugging
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.map
import java.util.*

class ConnectionStatusModel(val connection: CarConnectionDebugging, val carInfo: CarInformation): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			val handler = Handler(Looper.getMainLooper())
			var model: ConnectionStatusModel? = null
			val connection = CarConnectionDebugging(appContext) {
				handler.post { model?.update() }
			}
			val carInfo = CarInformationObserver {
				handler.post { model?.update() }
			}
			// can't unittest CarConnectionDebugging registration
			connection.register()
			model = ConnectionStatusModel(connection, carInfo)
			model.update()
			return model as T
		}
	}

	// Security service delay
	private val SECURITY_SERVICE_THRESHOLD = 2000L
	private val _creationTime = System.currentTimeMillis()
	private val _age
		get() = System.currentTimeMillis() - _creationTime

	// Bluetooth
	private val _isBtConnected = MutableLiveData<Boolean>()
	val isBtConnected: LiveData<Boolean> = _isBtConnected
	private val _isA2dpConnected = MutableLiveData<Boolean>()
	val isA2dpConnected: LiveData<Boolean> = _isA2dpConnected
	private val _isSppAvailable = MutableLiveData<Boolean>()
	val isSppAvailable: LiveData<Boolean> = _isSppAvailable

	// USB
	private val _isUsbConnected = MutableLiveData<Boolean>()
	val isUsbConnected: LiveData<Boolean> = _isUsbConnected
	private val _isUsbCharging = MutableLiveData<Boolean>()
	val isUsbCharging: LiveData<Boolean> = _isUsbCharging
	private val _isUsbTransfer = MutableLiveData<Boolean>()
	val isUsbTransfer: LiveData<Boolean> = _isUsbTransfer
	private val _isUsbAccessory = MutableLiveData<Boolean>()
	val isUsbAccessory: LiveData<Boolean> = _isUsbAccessory
	private val _hintUsbAccessory = MutableLiveData<Context.() -> String> { "" }
	val hintUsbAccessory: LiveData<Context.() -> String> = _hintUsbAccessory

	// BCL connection delay
	private val BCL_READY_THRESHOLD = 10000L
	private var _bclReadyTimer: Job? = null

	// BCL
	private val _isBclReady = MutableLiveData<Boolean>()
	val isBclReady: LiveData<Boolean> = _isBclReady   // BT-SPP or USB-ACC is ready
	private val _isBclDisconnected = MutableLiveData<Boolean>()
	val isBclDisconnected: LiveData<Boolean> = _isBclDisconnected
	private val _hintBclDisconnected = MutableLiveData<Context.() -> String> {""}
	val hintBclDisconnected: LiveData<Context.() -> String> = _hintBclDisconnected
	private val _isBclConnecting = MutableLiveData<Boolean>()
	val isBclConnecting: LiveData<Boolean> = _isBclConnecting
	private val _isBclStuck = MutableLiveData<Boolean>()
	val isBclStuck: LiveData<Boolean> = _isBclStuck
	private val _isBclConnected = MutableLiveData<Boolean>()
	val isBclConnected: LiveData<Boolean> = _isBclConnected
	private val _hintBclMode = MutableLiveData<Context.() -> String>()
	val hintBclMode: LiveData<Context.() -> String> = _hintBclMode
	private val _bclTransport = MutableLiveData("")
	val bclTransport: LiveData<String> = _bclTransport
	private val _bclModeText = MutableLiveData<Context.() -> String> {
		getString(R.string.txt_setup_bcl_connected)
	}
	val bclModeText: LiveData<Context.() -> String> = _bclModeText
	val isBclTransportBT: LiveData<Boolean> = bclTransport.map {
		it == "BT"
	}

	// Car
	val isCarConnected = isBclConnected
	private val _carBrand = MutableLiveData<String?>(null)
	val carBrand: LiveData<String?> = _carBrand
	private val _carLogo = MutableLiveData<Context.() -> Drawable?> { null }
	val carLogo: LiveData<Context.() -> Drawable?> = _carLogo
	private val _carChassisCode = MutableLiveData<ChassisCode?>()
	val carChassisCode = _carChassisCode
	private val _carConnectionText = MutableLiveData<Context.() -> String> { getString(R.string.connectionStatusWaiting) }
	val carConnectionText: LiveData<Context.() -> String> = _carConnectionText
	private val _carConnectionHint = MutableLiveData<Context.() -> String> {""}
	val carConnectionHint: LiveData<Context.() -> String> = _carConnectionHint
	private val _carConnectionColor = MutableLiveData<Context.() -> Int> {ContextCompat.getColor(this, R.color.connectionWaiting)}
	val carConnectionColor: LiveData<Context.() -> Int> = _carConnectionColor

	private val hmiVersionMatcher = Regex("^[A-Za-z0-9_]*(?=_[0-9])")   // look for the first numeric segment and split before
	private val hmiMatcher = Regex("^[A-Za-z0-9]*(?=_)")        // fallback to split on the first underscore
	private val _hmiVersion = MutableLiveData<String>(null)
	val hmiVersion: LiveData<String> = _hmiVersion


	fun update() {
		var connectingStatus: Context.() -> String = { getString(R.string.connectionStatusWaiting) }
		var connectingHint: Context.() -> String = { "" }

		_isBtConnected.value = connection.isBTConnected
		_isA2dpConnected.value = connection.isA2dpConnected
		_isSppAvailable.value = connection.isSPPAvailable

		_isUsbConnected.value = connection.isUsbConnected
		_isUsbCharging.value = connection.isUsbConnected && !connection.isUsbTransferConnected && !connection.isUsbAccessoryConnected
		_isUsbTransfer.value = connection.isUsbConnected && connection.isUsbTransferConnected && !connection.isUsbAccessoryConnected
		_isUsbAccessory.value = connection.isUsbConnected && connection.isUsbAccessoryConnected
		_hintUsbAccessory.value = {getString(R.string.txt_setup_enable_usbacc, connection.deviceName)}
		if (_isUsbCharging.value == true) {
			connectingHint = { getString(R.string.txt_setup_enable_usbmtp) }
		} else if (_isUsbTransfer.value == true) {
			connectingHint = { getString(R.string.txt_setup_enable_usbacc, connection.deviceName) }
		}

		val oldBclReady = _isBclReady.value ?: false
		val newBclReady = (connection.isSPPAvailable || connection.isUsbAccessoryConnected || connection.isBCLConnected)
		if (!oldBclReady && newBclReady) {
			_hintBclDisconnected.value = { "" }
			// fire off a timer and set the connection hint after the timer finishes
			// if the connection has succeeded, this panel is hidden before the hint shows
			_bclReadyTimer?.cancel()
			_bclReadyTimer = viewModelScope.launch {
				delay(BCL_READY_THRESHOLD)
				if (_isBclConnected.value != true) {
					if (connection.isBMWMineInstalled && connection.btCarBrand == "BMW") {
						_hintBclDisconnected.value = { getString(R.string.txt_setup_enable_bmwmine) }
						_carConnectionHint.value = { getString(R.string.txt_setup_enable_bmwmine) }
					} else if (connection.isMiniMineInstalled && connection.btCarBrand == "MINI") {
						_hintBclDisconnected.value = { getString(R.string.txt_setup_enable_minimine) }
						_carConnectionHint.value = { getString(R.string.txt_setup_enable_minimine) }
					} else {
						_hintBclDisconnected.value = { getString(R.string.txt_setup_enable_bclspp_usb) }
						_carConnectionHint.value = { getString(R.string.txt_setup_enable_bclspp_usb) }
					}
				}
			}
		}

		_isBclReady.value = newBclReady
		_isBclDisconnected.value = !connection.isBCLConnecting && !connection.isBCLConnected
		_isBclConnecting.value = connection.isBCLConnecting && !connection.isBCLConnected
		_isBclStuck.value = connection.isBCLStuck
		_isBclConnected.value = connection.isBCLConnected
		_hintBclMode.value = {getString(R.string.txt_setup_enable_bcl_mode, connection.deviceName)}
		_bclTransport.value = connection.bclTransport?.uppercase(Locale.ROOT) ?: ""
		_bclModeText.value = if (connection.bclTransport == null) {
			{ getString(R.string.txt_setup_bcl_connected) }
		} else {
			{ getString(R.string.txt_setup_bcl_connected_transport, connection.bclTransport) }
		}

		if (newBclReady && _isBclDisconnected.value == true) {
			connectingStatus = { getString(R.string.txt_setup_bcl_waiting) }
		}
		if (connection.isBCLConnecting || connection.isBCLConnected) {
			// tunnel is connecting or connected
			// connectingStatus will be ignored if carInfo is ready
			connectingStatus = { getString(R.string.txt_setup_bcl_connecting) }
		}
		if (_isBclStuck.value == true) {
			connectingHint = {getString(R.string.txt_setup_enable_bcl_mode, connection.deviceName)}
		}

		// current car overview
		val brand = if (connection.isBCLConnected) connection.carBrand?.uppercase(Locale.ROOT) else null
		_carBrand.value = brand
		when (brand) {
			"BMW" -> _carLogo.value = { ContextCompat.getDrawable(this, R.drawable.logo_bmw) }
			"MINI" -> _carLogo.value = { ContextCompat.getDrawable(this, R.drawable.logo_mini) }
			else -> _carLogo.value = { null }
		}

		val chassisCode = ChassisCode.fromCode(carInfo.capabilities["vehicle.type"] ?: "Unknown")
		_carChassisCode.value = chassisCode

		if (!connection.isConnectedSecurityConnected && !connection.isConnectedSecurityConnecting && _age > SECURITY_SERVICE_THRESHOLD) {
			_carConnectionText.value = { getString(R.string.connectionStatusMissingConnectedApp) }
			_carConnectionHint.value = { "" }
			_carConnectionColor.value = { ContextCompat.getColor(this, R.color.connectionError) }
		} else if (connection.isBCLConnected && chassisCode != null) {
			_carConnectionText.value = { getString(R.string.connectionStatusConnected, chassisCode.toString()) }
			_carConnectionHint.value = { "" }
			_carConnectionColor.value = { ContextCompat.getColor(this, R.color.connectionConnected) }
		} else if (connection.isBCLConnected && brand != null) {
			_carConnectionText.value = { getString(R.string.connectionStatusConnected, brand) }
			_carConnectionHint.value = { "" }
			_carConnectionColor.value = { ContextCompat.getColor(this, R.color.connectionConnected) }
		} else {
			_carConnectionText.value = connectingStatus
			_carConnectionHint.value = connectingHint
			_carConnectionColor.value = { ContextCompat.getColor(this, R.color.connectionWaiting) }
		}

		val hmiType = carInfo.capabilities["hmi.type"] ?: ""
		val hmiVersion = carInfo.capabilities["hmi.version"] ?: ""
		val mainHmiVersion = (hmiVersionMatcher.find(hmiVersion) ?: hmiMatcher.find(hmiVersion))?.value ?: ""
		// recent ID5/6 versions just say EntryEvo and don't have a specific category
		val displayedHmiVersion = if (mainHmiVersion.contains("EntryEvo_ID5")) hmiType else mainHmiVersion
		_hmiVersion.value = displayedHmiVersion
	}

	fun onPause() {
		// clear boolean statuses to stop animations
		_isBtConnected.value = false
		_isA2dpConnected.value = false
		_isSppAvailable.value = false
		_isUsbConnected.value = false
		_isUsbCharging.value = false
		_isUsbTransfer.value = false
		_isUsbAccessory.value = false
		_isBclReady.value = false
		_isBclDisconnected.value = false
		_isBclConnecting.value = false
		_isBclStuck.value = false
	}

	override fun onCleared() {
		super.onCleared()
		connection.unregister()
	}
}