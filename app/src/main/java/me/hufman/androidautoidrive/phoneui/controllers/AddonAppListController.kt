package me.hufman.androidautoidrive.phoneui.controllers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import me.hufman.androidautoidrive.addons.AddonAppInfo
import me.hufman.androidautoidrive.utils.PackageManagerCompat.resolveActivityCompat

class AddonAppListController(val context: Context, val permissionsController: PermissionsController) {
	fun openApplicationPermissions(appInfo: AddonAppInfo) {
		permissionsController.openApplicationPermissions(appInfo.packageName)
	}

	private fun tryOpenActivity(intent: Intent): Boolean {
		if (context.packageManager.resolveActivityCompat(intent, 0) != null) {
			try {
				context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
				return true
			} catch (e: ActivityNotFoundException) {
			} catch (e: IllegalArgumentException) {}
		}
		return false
	}

	fun openIntent(intent: Intent?) {
		if (intent != null) {
			tryOpenActivity(intent)
		}
	}
}