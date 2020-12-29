package me.hufman.androidautoidrive.phoneui.viewmodels

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import me.hufman.androidautoidrive.notifications.NotificationListenerServiceImpl

class PermissionsModel(val notificationListenerState: LiveData<Boolean>, val permissionsState: PermissionsState): ViewModel(), Observer<Boolean> {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel?> create(modelClass: Class<T>): T {
			val viewModel = PermissionsModel(NotificationListenerServiceImpl.serviceState, PermissionsState(appContext))
			viewModel.subscribe()
			return viewModel as T
		}
	}

	private val _hasNotificationPermission = MutableLiveData<Boolean>(false)
	val hasNotificationPermission = _hasNotificationPermission as LiveData<Boolean>
	private val _hasSmsPermission = MutableLiveData<Boolean>(false)
	val hasSmsPermission = _hasSmsPermission as LiveData<Boolean>
	private val _hasLocationPermission = MutableLiveData<Boolean>(false)
	val hasLocationPermission = _hasLocationPermission as LiveData<Boolean>

	fun update() {
		_hasNotificationPermission.value = notificationListenerState.value == true && permissionsState.hasNotificationPermission
		_hasSmsPermission.value = permissionsState.hasSmsPermission
		_hasLocationPermission.value = permissionsState.hasLocationPermission
	}

	// subscription management
	private fun subscribe() {
		notificationListenerState.observeForever(this)
	}
	private fun unsubscribe() {
		notificationListenerState.removeObserver(this)
	}

	override fun onChanged(t: Boolean?) {
		// an observed LiveData has updated
		update()
	}

	override fun onCleared() {
		unsubscribe()
	}
}

class PermissionsState(private val appContext: Context) {
	val hasNotificationPermission: Boolean
		get() {
			val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(appContext)
			return enabledListeners.contains(appContext.packageName)
		}

	val hasSmsPermission: Boolean
		get() = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

	val hasLocationPermission: Boolean
		get() = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}