package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.phoneui.MusicAppDiscoveryThread

class MusicViewModel(val musicAppDiscoveryThread: MusicAppDiscoveryThread): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel?> create(modelClass: Class<T>): T {
			var viewModel: MusicViewModel? = null
			val musicAppDiscoveryThread = MusicAppDiscoveryThread(appContext) {
				viewModel?.setApps(it.allApps)
			}
			viewModel = MusicViewModel(musicAppDiscoveryThread)
			musicAppDiscoveryThread.start()
			return viewModel as T
		}
	}

	private val _apps = MutableLiveData<List<MusicAppInfo>>(emptyList())
	val apps: LiveData<List<MusicAppInfo>> = _apps

	fun forceDiscovery() {
		musicAppDiscoveryThread.forceDiscovery()
	}

	private fun setApps(apps: List<MusicAppInfo>) {
		_apps.postValue(apps)
	}

	override fun onCleared() {
		super.onCleared()
		musicAppDiscoveryThread.stopDiscovery()
	}
}