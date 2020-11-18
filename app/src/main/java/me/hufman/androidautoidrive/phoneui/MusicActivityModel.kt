package me.hufman.androidautoidrive.phoneui

import android.arch.lifecycle.ViewModel
import android.graphics.Bitmap
import me.hufman.androidautoidrive.music.MusicController

class MusicActivityModel(): ViewModel() {
	var musicController: MusicController? = null
	val icons = HashMap<String, Bitmap>()

	override fun onCleared() {
		super.onCleared()
		musicController?.disconnectApp(pause=false)
	}
}