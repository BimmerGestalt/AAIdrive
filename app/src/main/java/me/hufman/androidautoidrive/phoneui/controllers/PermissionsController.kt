package me.hufman.androidautoidrive.phoneui.controllers

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import io.bimmergestalt.idriveconnectkit.android.security.KnownSecurityServices
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import me.hufman.androidautoidrive.music.spotify.SpotifyAuthStateManager
import me.hufman.androidautoidrive.phoneui.SpotifyAuthorizationActivity
import me.hufman.androidautoidrive.utils.PackageManagerCompat.resolveActivityCompat

class PermissionsController(val activity: Activity) {
	companion object {
		const val REQUEST_SMS = 20
		const val REQUEST_CALENDAR = 30
		const val REQUEST_LOCATION = 4000
		const val REQUEST_BLUETOOTH = 50
		const val REQUEST_POST_NOTIFICATIONS = 60
		const val REQUEST_ASSISTANT = 70
	}

	private fun tryOpenActivity(intent: Intent): Boolean {
		if (activity.packageManager.resolveActivityCompat(intent, 0) != null) {
			try {
				activity.startActivity(intent)
				return true
			} catch (e: ActivityNotFoundException) {
			} catch (e: IllegalArgumentException) {}
		}
		return false
	}

	fun openApplicationPermissions(packageName: String) {
		run {
			val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
			intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
			intent.setClassName("com.miui.securitycenter",
					"com.miui.permcenter.permissions.PermissionsEditorActivity")
			intent.putExtra("extra_pkgname", packageName)
			if (tryOpenActivity(intent)) return
		}
		run {
			val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
			intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
			// try an implicit intent without a classname
			intent.putExtra("extra_pkgname", packageName)
			if (tryOpenActivity(intent)) return
		}
		run {
			val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
			intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
			intent.data = Uri.fromParts("package", packageName, null)
			if (tryOpenActivity(intent)) return
		}
		run {
			val intent = Intent(Settings.ACTION_SETTINGS)
			intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
			if (tryOpenActivity(intent)) return
		}
	}

	fun openSelfPermissions() {
		openApplicationPermissions(activity.packageName)
	}

	fun openBmwMinePermissions() {
		for (app in KnownSecurityServices.entries) {
			try {
				if (activity.packageManager.getPackageInfo(app.packageName, 0) != null) {
					openApplicationPermissions(app.packageName)
					break
				}
			} catch (_: Exception) {}
		}
	}

	fun openMiniMinePermissions() {
		for (app in KnownSecurityServices.entries) {
			try {
				if (activity.packageManager.getPackageInfo(app.packageName, 0) != null) {
					openApplicationPermissions(app.packageName)
					break
				}
			} catch (_: Exception) {}
		}
	}

	fun promptNotification() {
		val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		activity.startActivity(intent)
	}

	fun promptPostNotificationsPermission() {
		if (android.os.Build.VERSION.SDK_INT >= 33 && activity.applicationInfo.targetSdkVersion >= 33) {       // Android 13+
			ActivityCompat.requestPermissions(activity,
					arrayOf(Manifest.permission.POST_NOTIFICATIONS),
					REQUEST_POST_NOTIFICATIONS)
		} else {
			openApplicationPermissions(activity.packageName)
		}
	}

	fun promptFullscreen() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			ActivityCompat.requestPermissions(activity,
				arrayOf(Manifest.permission.USE_FULL_SCREEN_INTENT),
				REQUEST_ASSISTANT)
		}
	}

	fun promptSms() {
		ActivityCompat.requestPermissions(activity,
				arrayOf(Manifest.permission.READ_SMS),
				REQUEST_SMS)
	}

	fun promptCalendar() {
		ActivityCompat.requestPermissions(activity,
				arrayOf(Manifest.permission.READ_CALENDAR),
				REQUEST_CALENDAR)
	}

	fun promptLocation() {
		ActivityCompat.requestPermissions(activity,
				arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
				REQUEST_LOCATION)
	}

	fun promptBluetooth() {
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
			ActivityCompat.requestPermissions(activity,
					arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
					REQUEST_BLUETOOTH)
		}
	}

	fun promptSpotifyControl() {
		val connector = SpotifyAppController.Connector(activity, true)
		connector.connect().apply {
			callback = { it?.disconnect() }
		}
	}

	fun clearSpotifyControl() {
		val spotifyUrl = "http://www.spotify.com/account/apps/"
		val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUrl))
		intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
		activity.startActivity(intent)
	}

	fun promptSpotifyAuthorization() {
		val intent = Intent(activity.applicationContext, SpotifyAuthorizationActivity::class.java)
		activity.startActivity(intent)
	}

	fun clearSpotifyAuthorization() {
		val authStateManager = SpotifyAuthStateManager.getInstance(MutableAppSettingsReceiver(activity))
		authStateManager.clear()
	}
}