package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.os.Handler
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.connections.CarConnectionDebugging

class DependencyInfoModel(val connection: CarConnectionDebugging): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			val handler = Handler()
			var model: DependencyInfoModel? = null
			val connection = CarConnectionDebugging(appContext) {
				handler.post { model?.update() }
			}
			// can't unittest CarConnectionDebugging registration
			connection.register()
			model = DependencyInfoModel(connection)
			model.update()
			return model as T
		}
	}

	private val _isBmwConnectedInstalled = MutableLiveData<Boolean>()
	val isBmwConnectedInstalled: LiveData<Boolean> = _isBmwConnectedInstalled
	private val _isMiniConnectedInstalled = MutableLiveData<Boolean>()
	val isMiniConnectedInstalled: LiveData<Boolean> = _isMiniConnectedInstalled
	private val _isBmwReady = MutableLiveData<Boolean>()
	val isBmwReady: LiveData<Boolean> = _isBmwReady
	private val _isMiniReady = MutableLiveData<Boolean>()
	val isMiniReady: LiveData<Boolean> = _isMiniReady
	private val _isBmwMineInstalled = MutableLiveData<Boolean>()
	val isBmwMineInstalled: LiveData<Boolean> = _isBmwMineInstalled
	private val _isMiniMineInstalled = MutableLiveData<Boolean>()
	val isMiniMineInstalled: LiveData<Boolean> = _isMiniMineInstalled

	// Connected Security Service is installed but not connected
	private val _isSecurityServiceDisconnected = MutableLiveData<Boolean>()
	val isSecurityServiceDisconnected: LiveData<Boolean> = _isSecurityServiceDisconnected

	fun update() {
		_isBmwConnectedInstalled.value = connection.isBMWConnectedInstalled
		_isMiniConnectedInstalled.value = connection.isMiniConnectedInstalled
		_isBmwReady.value = connection.isBMWConnectedInstalled && connection.isConnectedSecurityConnected
		_isMiniReady.value = connection.isMiniConnectedInstalled && connection.isConnectedSecurityConnected
		_isBmwMineInstalled.value = connection.isBMWMineInstalled
		_isMiniMineInstalled.value = connection.isMiniMineInstalled
		_isSecurityServiceDisconnected.value = connection.isConnectedSecurityInstalled && !connection.isConnectedSecurityConnected
	}

	override fun onCleared() {
		super.onCleared()
		connection.unregister()
	}
}