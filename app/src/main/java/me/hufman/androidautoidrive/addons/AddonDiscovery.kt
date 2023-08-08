package me.hufman.androidautoidrive.addons

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.drawable.ColorDrawable
import me.hufman.androidautoidrive.utils.PackageManagerCompat.getPackageInfoCompat
import me.hufman.androidautoidrive.utils.PackageManagerCompat.queryIntentServicesCompat
import me.hufman.androidautoidrive.utils.PackageManagerCompat.resolveActivityCompat

class AddonDiscovery(val packageManager: PackageManager) {
	companion object {
		const val PERMISSION_NORMAL = "io.bimmergestalt.permission.CDS_normal"
		const val PERMISSION_PERSONAL = "io.bimmergestalt.permission.CDS_personal"
		const val INTENT_CONNECTION_SERVICE = "io.bimmergestalt.carconnection.service"
		const val INTENT_DATA_SERVICE = "io.bimmergestalt.cardata.service"

		private const val ACTION_APPLICATION_PREFERENCES = "android.intent.action.APPLICATION_PREFERENCES"  // API 24
	}

	private fun resolveIntent(intent: Intent): Intent? {
		val resolveInfo = packageManager.resolveActivityCompat(intent, 0)
		if (resolveInfo != null) {
			intent.setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
			return intent
		}
		return null
	}

	fun discoverApps(): List<AddonAppInfo> {
		val discovered = HashMap<String, AddonAppInfo>()

		val intentDataService = Intent(INTENT_DATA_SERVICE)
		packageManager.queryIntentServicesCompat(intentDataService, 0).forEach { resolveInfo ->
			// load up the requested permissions for this app
			val packageName = resolveInfo.serviceInfo.packageName
			val packageInfo = packageManager.getPackageInfoCompat(packageName, PackageManager.GET_PERMISSIONS)

			var cdsNormalRequested = false
			var cdsPersonalRequested = false
			packageInfo?.requestedPermissions?.forEach {
				if (it == PERMISSION_NORMAL) {
					cdsNormalRequested = true
				}
				if (it == PERMISSION_PERSONAL) {
					cdsPersonalRequested = true
				}
			}
			if (cdsNormalRequested || cdsPersonalRequested) {
				val name = packageInfo?.applicationInfo?.loadLabel(packageManager)?.toString() ?: ""
				val icon = packageInfo?.applicationInfo?.loadIcon(packageManager) ?: ColorDrawable(0)
				val cdsNormalGranted = packageManager.checkPermission(PERMISSION_NORMAL, packageName) == PERMISSION_GRANTED
				val cdsPersonalGranted = packageManager.checkPermission(PERMISSION_PERSONAL, packageName) == PERMISSION_GRANTED
				val appInfo = AddonAppInfo(name, icon, packageName).also {
					it.intentOpen = resolveIntent(Intent(Intent.ACTION_MAIN).setPackage(packageName))
					it.intentSettings = resolveIntent(Intent(ACTION_APPLICATION_PREFERENCES).setPackage(packageName))
					it.intentDataService = Intent(INTENT_DATA_SERVICE).setPackage(packageName)
					it.cdsNormalRequested = cdsNormalRequested
					it.cdsNormalGranted = cdsNormalGranted
					it.cdsPersonalRequested = cdsPersonalRequested
					it.cdsPersonalGranted = cdsPersonalGranted
				}
				discovered[packageName] = appInfo
			}
		}

		val intentCarService = Intent(INTENT_CONNECTION_SERVICE)
		packageManager.queryIntentServicesCompat(intentCarService, 0).forEach { resolveInfo ->
			val packageName = resolveInfo.serviceInfo.packageName
			val packageInfo = packageManager.getPackageInfoCompat(packageName, 0)
			val name = packageInfo?.applicationInfo?.loadLabel(packageManager)?.toString() ?: ""
			val icon = packageInfo?.applicationInfo?.loadIcon(packageManager) ?: ColorDrawable(0)
			val appInfo = discovered[packageName] ?: AddonAppInfo(name, icon, packageName).also {
				it.intentOpen = resolveIntent(Intent(Intent.ACTION_MAIN).setPackage(packageName))
				it.intentSettings = resolveIntent(Intent(ACTION_APPLICATION_PREFERENCES).setPackage(packageName))
				it.intentConnectionService = Intent(INTENT_CONNECTION_SERVICE).setPackage(packageName)
				// a direct car connection can provide all the same
				it.cdsNormalRequested = true
				it.cdsNormalGranted = true
				it.cdsPersonalRequested = true
				it.cdsPersonalGranted = true
				// show the checkboxes about the car connection
				it.carConnectionRequested = true
				it.carConnectionGranted = true     // TODO support disabling like the MusicApp swiping
			}
			discovered[packageName] = appInfo
		}

		val results = ArrayList(discovered.values)
		results.sortBy { it.name }

		return results
	}
}