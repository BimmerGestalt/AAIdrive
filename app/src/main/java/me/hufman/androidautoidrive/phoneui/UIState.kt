package me.hufman.androidautoidrive.phoneui

import me.hufman.androidautoidrive.music.MusicAppInfo

object UIState {
	@Volatile var notificationListenerConnected = false
	var selectedMusicApp: MusicAppInfo? = null
}