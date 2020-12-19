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
	val isBmwConnectedInstalled = _isBmwConnectedInstalled as LiveData<Boolean>
	private val _isMiniConnectedInstalled = MutableLiveData<Boolean>()
	val isMiniConnectedInstalled = _isMiniConnectedInstalled as LiveData<Boolean>
	private val _isBmwReady = MutableLiveData<Boolean>()
	val isBmwReady = _isBmwReady as LiveData<Boolean>
	private val _isMiniReady = MutableLiveData<Boolean>()
	val isMiniReady = _isMiniReady as LiveData<Boolean>
	private val _isBmwMineInstalled = MutableLiveData<Boolean>()
	val isBmwMineInstalled = _isBmwMineInstalled as LiveData<Boolean>
	private val _isMiniMineInstalled = MutableLiveData<Boolean>()
	val isMiniMineInstalled = _isMiniMineInstalled as LiveData<Boolean>

	// Connected Security Service is installed but not connected
	private val _isSecurityServiceDisconnected = MutableLiveData<Boolean>()
	val isSecurityServiceDisconnected = _isSecurityServiceDisconnected as LiveData<Boolean>

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