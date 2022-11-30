package me.hufman.androidautoidrive.phoneui.controllers

import android.app.Activity
import android.content.Intent
import android.view.View
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.phoneui.MusicPlayerActivity
import me.hufman.androidautoidrive.phoneui.UIState
import me.hufman.androidautoidrive.phoneui.ViewHelpers.visible

class MusicAppListController(val activity: Activity, val permissionsController: PermissionsController) {
	fun openApplicationPermissions(appInfo: MusicAppInfo) {
		permissionsController.openApplicationPermissions(appInfo.packageName)
	}

	fun openMusicApp(appInfo: MusicAppInfo) {
		UIState.selectedMusicApp = appInfo
		val intent = Intent(activity, MusicPlayerActivity::class.java)
		activity.startActivity(intent)
	}

	fun toggleFeatures(view: View) {
		view.visible = !view.visible
	}
}