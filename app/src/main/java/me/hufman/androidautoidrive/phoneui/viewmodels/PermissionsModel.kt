package me.hufman.androidautoidrive.phoneui.viewmodels

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import me.hufman.androidautoidrive.Observable
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import me.hufman.androidautoidrive.notifications.NotificationListenerServiceImpl

class PermissionsModel(private val notificationListenerState: LiveData<Boolean>,
                       private val permissionsState: PermissionsState,
                       private val spotifyConnector: SpotifyAppController.Connector): ViewModel(), Observer<Boolean> {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel?> create(modelClass: Class<T>): T {
			val viewModel = PermissionsModel(NotificationListenerServiceImpl.serviceState,
					PermissionsState(appContext),
					SpotifyAppController.Connector(appContext, false))
			viewModel.subscribe()
			return viewModel as T
		}
	}

	private val _hasNotificationPermission = MutableLiveData(false)
	val hasNotificationPermission = _hasNotificationPermission as LiveData<Boolean>
	private val _hasSmsPermission = MutableLiveData<Boolean>(false)
	val hasSmsPermission = _hasSmsPermission as LiveData<Boolean>
	private val _hasLocationPermission = MutableLiveData<Boolean>(false)
	val hasLocationPermission = _hasLocationPermission as LiveData<Boolean>

	private val _hasSpotify = MutableLiveData(false)
	val hasSpotify = _hasSpotify as LiveData<Boolean>
	private val _hasSpotifyControlPermission = MutableLiveData(spotifyConnector.previousControlSuccess())
	val hasSpotifyControlPermission = _hasSpotifyControlPermission as LiveData<Boolean>
	private val _spotifyErrorHint = MutableLiveData<Context.() -> String> { "" }
	val spotifyHint = _spotifyErrorHint as LiveData<Context.() -> String>

	fun update() {
		_hasNotificationPermission.value = notificationListenerState.value == true && permissionsState.hasNotificationPermission
		_hasSmsPermission.value = permissionsState.hasSmsPermission
		_hasLocationPermission.value = permissionsState.hasLocationPermission
	}

	/** Try connecting to Spotify */
	fun updateSpotify() {
		val hasSpotify = spotifyConnector.isSpotifyInstalled() && spotifyConnector.hasSupport()
		_hasSpotify.value = hasSpotify

		if (hasSpotify) {
			spotifyConnector.connect().apply {
				callback = { _updateSpotify(); it?.disconnect() }
			}
		}
	}

	private fun _updateSpotify() {
		_hasSpotifyControlPermission.value = spotifyConnector.previousControlSuccess()
		val errorName = spotifyConnector.lastError?.javaClass?.simpleName
		val errorMessage = spotifyConnector.lastError?.message
		when(errorName) {
			"CouldNotFindSpotifyApp" -> _spotifyErrorHint.value = { getString(R.string.musicAppNotes_spotify_apiNotFound) }
			"OfflineModeException" -> _spotifyErrorHint.value = { getString(R.string.musicAppNotes_spotify_apiUnavailable) }
			"UserNotAuthorizedException" -> when {
				// no internet
				errorMessage?.contains("AUTHENTICATION_SERVICE_UNAVAILABLE") == true -> {
					_spotifyErrorHint.value = { getString(R.string.musicAppNotes_spotify_apiUnavailable) }
				}
				// user didn't grant access or user cancelled
				errorMessage?.contains("User authorization required") == true -> {
					_spotifyErrorHint.value = { ""}
				}
				// unknown
				else -> {
					_spotifyErrorHint.value = { errorMessage ?: "" }
				}
			}
			else -> _spotifyErrorHint.value = { errorMessage ?: "" }
		}
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