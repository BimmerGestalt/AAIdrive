package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.BackgroundInterruptionDetection
import me.hufman.androidautoidrive.connections.CarConnectionDebugging

class DependencyInfoModel(val connection: CarConnectionDebugging, val interruptionDetection: BackgroundInterruptionDetection): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			val handler = Handler(Looper.getMainLooper())
			var model: DependencyInfoModel? = null
			val connection = CarConnectionDebugging(appContext) {
				handler.post { model?.update() }
			}
			// can't unittest CarConnectionDebugging registration
			// don't actually need to subscribe to the connection status for devices or bcl
			// the callback automatically provides SecurityService callbacks
			model = DependencyInfoModel(connection, BackgroundInterruptionDetection.build(appContext))
			model.update()
			return model as T
		}
	}

	private val _isBmwConnectedInstalled = MutableLiveData<Boolean>()
	val isBmwConnectedInstalled: LiveData<Boolean> = _isBmwConnectedInstalled
	private val _isMiniConnectedInstalled = MutableLiveData<Boolean>()
	val isMiniConnectedInstalled: LiveData<Boolean> = _isMiniConnectedInstalled
	private val _isBmwConnected65Installed = MutableLiveData<Boolean>()
	val isBmwConnected65Installed: LiveData<Boolean> = _isBmwConnected65Installed
	private val _isMiniConnected65Installed = MutableLiveData<Boolean>()
	val isMiniConnected65Installed: LiveData<Boolean> = _isMiniConnected65Installed
	private val _isBmwMineInstalled = MutableLiveData<Boolean>()
	val isBmwMineInstalled: LiveData<Boolean> = _isBmwMineInstalled
	private val _isMiniMineInstalled = MutableLiveData<Boolean>()
	val isMiniMineInstalled: LiveData<Boolean> = _isMiniMineInstalled

	private val _isMiniInstalled = MutableLiveData<Boolean>()
	val isMiniInstalled: LiveData<Boolean> = _isMiniInstalled
	private val _isBmwInstalled = MutableLiveData<Boolean>()
	val isBmwInstalled: LiveData<Boolean> = _isBmwInstalled

	private val _isBmwReady = MutableLiveData<Boolean>()
	val isBmwReady: LiveData<Boolean> = _isBmwReady
	private val _isMiniReady = MutableLiveData<Boolean>()
	val isMiniReady: LiveData<Boolean> = _isMiniReady

	// Connected Security Service is installed but not connected
	private val _isSecurityServiceDisconnected = MutableLiveData<Boolean>()
	val isSecurityServiceDisconnected: LiveData<Boolean> = _isSecurityServiceDisconnected

	// Background restrictions appear to be in effect
	val _hasBackgroundKilled = MutableLiveData(false)
	val hasBackgroundKilled: LiveData<Boolean> = _hasBackgroundKilled
	val _hasBackgroundSuspended = MutableLiveData(false)
	val hasBackgroundSuspended: LiveData<Boolean> = _hasBackgroundSuspended

	fun update() {
		_isBmwConnectedInstalled.value = connection.isBMWConnectedInstalled
		_isMiniConnectedInstalled.value = connection.isMiniConnectedInstalled
		_isBmwConnected65Installed.value = connection.isBMWConnected65Installed
		_isMiniConnected65Installed.value = connection.isMiniConnected65Installed
		_isBmwMineInstalled.value = connection.isBMWMineInstalled
		_isMiniMineInstalled.value = connection.isMiniMineInstalled
		_isBmwInstalled.value = connection.isBMWInstalled
		_isMiniInstalled.value = connection.isMiniInstalled
		_isBmwReady.value = connection.isBMWInstalled && connection.isConnectedSecurityConnected
		_isMiniReady.value = connection.isMiniInstalled && connection.isConnectedSecurityConnected
		_isSecurityServiceDisconnected.value = connection.isConnectedSecurityInstalled && !connection.isConnectedSecurityConnected
		_hasBackgroundKilled.value = interruptionDetection.detectedKilled >= 3
		_hasBackgroundSuspended.value = interruptionDetection.detectedSuspended >= 2
	}

	override fun onCleared() {
		super.onCleared()
		connection.unregister()
	}
}