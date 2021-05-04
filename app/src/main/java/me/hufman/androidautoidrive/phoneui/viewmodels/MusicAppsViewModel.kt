package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.os.Handler
import androidx.databinding.ObservableArrayList
import androidx.databinding.ObservableList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.phoneui.MusicAppDiscoveryThread

class MusicAppsViewModel(val musicAppDiscoveryThread: MusicAppDiscoveryThread): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel?> create(modelClass: Class<T>): T {
			var model: MusicAppsViewModel? = null

			val handler = Handler() // UI thread handler
			val thread = MusicAppDiscoveryThread(appContext) { _ ->
				handler.post {
					model?.update()
				}
			}.apply { start() }

			model = MusicAppsViewModel(thread)
			return model as T
		}
	}

	// TODO: Replace with https://github.com/evant/binding-collection-adapter
	val allApps: ObservableList<MusicAppInfo> = ObservableArrayList()
	val validApps: ObservableList<MusicAppInfo> = ObservableArrayList()

	fun update() {
		allApps.clear()
		allApps.addAll(musicAppDiscoveryThread.discovery?.allApps ?: emptyList())
		validApps.clear()
		validApps.addAll(musicAppDiscoveryThread.discovery?.validApps ?: emptyList())
	}

	override fun onCleared() {
		super.onCleared()
		musicAppDiscoveryThread.stopDiscovery()
	}
}