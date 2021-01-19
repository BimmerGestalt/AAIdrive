package me.hufman.androidautoidrive.phoneui.controllers

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import me.hufman.androidautoidrive.phoneui.SpotifyAuthorizationActivity

class PermissionsController(val activity: Activity) {
	companion object {
		const val REQUEST_SMS = 20
		const val REQUEST_LOCATION = 4000
	}

	fun openApplicationPermissions(packageName: String) {
		run {
			val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
			intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
			intent.setClassName("com.miui.securitycenter",
					"com.miui.permcenter.permissions.PermissionsEditorActivity")
			intent.putExtra("extra_pkgname", packageName)
			try {
				activity.startActivity(intent)
				return
			} catch (e: ActivityNotFoundException) {}
		}
		run {
			val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
			intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
			// try an implicit intent without a classname
			intent.putExtra("extra_pkgname", packageName)
			try {
				activity.startActivity(intent)
				return
			} catch (e: ActivityNotFoundException) {}
		}
		run {
			val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
			intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
			intent.data = Uri.fromParts("package", packageName, null)
			try {
				activity.startActivity(intent)
				return
			} catch (e: ActivityNotFoundException) {}
		}
		run {
			val intent = Intent(Settings.ACTION_SETTINGS)
			intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
			try {
				activity.startActivity(intent)
				return
			} catch (e: ActivityNotFoundException) {}
		}
	}

	fun promptNotification() {
		val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		activity.startActivity(intent)
	}

	fun promptSms() {
		ActivityCompat.requestPermissions(activity,
				arrayOf(Manifest.permission.READ_SMS),
				REQUEST_SMS)
	}

	fun promptLocation() {
		ActivityCompat.requestPermissions(activity,
				arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
				REQUEST_LOCATION)
	}

	fun promptSpotifyControl() {
		val connector = SpotifyAppController.Connector(activity, true)
		connector.connect().apply {
			callback = { it?.disconnect() }
		}
	}

	fun promptSpotifyAuthorization() {
		val intent = Intent(activity.applicationContext, SpotifyAuthorizationActivity::class.java)
		activity.startActivity(intent)
	}
}