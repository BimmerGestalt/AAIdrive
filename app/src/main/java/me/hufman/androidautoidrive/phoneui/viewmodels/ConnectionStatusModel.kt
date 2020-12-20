package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.os.Handler
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.CarInformationObserver
import me.hufman.androidautoidrive.ChassisCode
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.connections.CarConnectionDebugging
import java.util.*

class ConnectionStatusModel(val connection: CarConnectionDebugging, val carInfo: CarInformation): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			val handler = Handler()
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

	// Bluetooth
	private val _isBtConnected = MutableLiveData<Boolean>()
	val isBtConnected = _isBtConnected as LiveData<Boolean>
	private val _isA2dpConnected = MutableLiveData<Boolean>()
	val isA2dpConnected = _isA2dpConnected as LiveData<Boolean>
	private val _isSppAvailable = MutableLiveData<Boolean>()
	val isSppAvailable = _isSppAvailable as LiveData<Boolean>

	// USB
	private val _isUsbConnected = MutableLiveData<Boolean>()
	val isUsbConnected = _isUsbConnected as LiveData<Boolean>
	private val _isUsbCharging = MutableLiveData<Boolean>()
	val isUsbCharging = _isUsbCharging as LiveData<Boolean>
	private val _isUsbTransfer = MutableLiveData<Boolean>()
	val isUsbTransfer = _isUsbTransfer as LiveData<Boolean>
	private val _isUsbAccessory = MutableLiveData<Boolean>()
	val isUsbAccessory = _isUsbAccessory as LiveData<Boolean>
	private val _hintUsbAccessory = MutableLiveData<Context.() -> String> { "" }
	val hintUsbAccessory = _hintUsbAccessory as LiveData<Context.() -> String>

	// BCL
	private val _isBclReady = MutableLiveData<Boolean>()
	val isBclReady = _isBclReady as LiveData<Boolean>   // BT-SPP or USB-ACC is ready
	private val _isBclDisconnected = MutableLiveData<Boolean>()
	val isBclDisconnected = _isBclDisconnected as LiveData<Boolean>
	private val _isBclConnecting = MutableLiveData<Boolean>()
	val isBclConnecting = _isBclConnecting as LiveData<Boolean>
	private val _isBclStuck = MutableLiveData<Boolean>()
	val isBclStuck = _isBclStuck as LiveData<Boolean>
	private val _isBclConnected = MutableLiveData<Boolean>()
	val isBclConnected = _isBclConnected as LiveData<Boolean>
	private val _hintBclMode = MutableLiveData<Context.() -> String>()
	val hintBclMode = _hintBclMode as LiveData<Context.() -> String>
	private val _bclModeText = MutableLiveData<Context.() -> String> {
		getString(R.string.txt_setup_bcl_connected)
	}
	val bclModeText = _bclModeText as LiveData<Context.() -> String>

	// Car
	val isCarConnected = isBclConnected
	private val _carChassisCode = MutableLiveData<ChassisCode?>()
	val carChassisCode = _carChassisCode
	private val _carConnectionText = MutableLiveData<Context.() -> String>()
	val carConnectionText = _carConnectionText as LiveData<Context.() -> String>

	fun update() {
		_isBtConnected.value = connection.isBTConnected
		_isA2dpConnected.value = connection.isA2dpConnected
		_isSppAvailable.value = connection.isSPPAvailable

		_isUsbConnected.value = connection.isUsbConnected
		_isUsbCharging.value = connection.isUsbConnected && !connection.isUsbTransferConnected && !connection.isUsbAccessoryConnected
		_isUsbTransfer.value = connection.isUsbConnected && connection.isUsbTransferConnected && !connection.isUsbAccessoryConnected
		_isUsbAccessory.value = connection.isUsbConnected && connection.isUsbAccessoryConnected
		_hintUsbAccessory.value = {getString(R.string.txt_setup_enable_usbacc, connection.deviceName)}

		_isBclReady.value = (connection.isSPPAvailable || connection.isUsbAccessoryConnected)
		_isBclDisconnected.value = !connection.isBCLConnecting && !connection.isBCLConnected
		_isBclConnecting.value = connection.isBCLConnecting && !connection.isBCLConnected
		_isBclStuck.value = connection.isBCLStuck
		_isBclConnected.value = connection.isBCLConnected
		_hintBclMode.value = {getString(R.string.txt_setup_enable_bcl_mode, connection.deviceName)}
		_bclModeText.value = if (connection.bclTransport == null) {
			{ getString(R.string.txt_setup_bcl_connected) }
		} else {
			{ getString(R.string.txt_setup_bcl_connected_transport, connection.bclTransport) }
		}

		val chassisCode = ChassisCode.fromCode(carInfo.capabilities["vehicle.type"] ?: "Unknown")
		_carChassisCode.value = chassisCode
		if (chassisCode == null) {
			val brand = connection.carBrand?.toLowerCase(Locale.ROOT)
			_carConnectionText.value = {
				when(brand) {
					"bmw" -> getString(R.string.notification_description_bmw)
					"mini" -> getString(R.string.notification_description_mini)
					else -> getString(R.string.notification_description)
				}
			}
		} else {
			_carConnectionText.value = { getString(R.string.notification_description_chassiscode, chassisCode.toString())}
		}
	}

	override fun onCleared() {
		super.onCleared()
		connection.unregister()
	}
}