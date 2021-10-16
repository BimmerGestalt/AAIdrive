package me.hufman.androidautoidrive.addons

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED

class AddonDiscovery(val packageManager: PackageManager) {
	companion object {
		const val PERMISSION_NORMAL = "io.bimmergestalt.permission.CDS_normal"
		const val PERMISSION_PERSONAL = "io.bimmergestalt.permission.CDS_personal"
		const val INTENT_CONNECTION_SERVICE = "io.bimmergestalt.carconnection.service"
		const val INTENT_DATA_SERVICE = "io.bimmergestalt.cardata.service"

		private const val ACTION_APPLICATION_PREFERENCES = "android.intent.action.APPLICATION_PREFERENCES"  // API 24
	}

	private fun resolveIntent(intent: Intent): Intent? {
		val resolveInfo = packageManager.resolveActivity(intent, 0)
		if (resolveInfo != null) {
			intent.setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
			return intent
		}
		return null
	}

	fun discoverApps(): List<AddonAppInfo> {
		val discovered = HashMap<String, AddonAppInfo>()

		val intentDataService = Intent(INTENT_DATA_SERVICE)
		packageManager.queryIntentServices(intentDataService, 0).forEach { resolveInfo ->
			// load up the requested permissions for this app
			val packageInfo = packageManager.getPackageInfo(resolveInfo.serviceInfo.packageName, PackageManager.GET_PERMISSIONS)

			var cdsNormalRequested = false
			var cdsPersonalRequested = false
			packageInfo.requestedPermissions?.forEach {
				if (it == PERMISSION_NORMAL) {
					cdsNormalRequested = true
				}
				if (it == PERMISSION_PERSONAL) {
					cdsPersonalRequested = true
				}
			}
			if (cdsNormalRequested || cdsPersonalRequested) {
				val name = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString()
				val icon = packageInfo.applicationInfo.loadIcon(packageManager)
				val cdsNormalGranted = packageManager.checkPermission(PERMISSION_NORMAL, packageInfo.packageName) == PERMISSION_GRANTED
				val cdsPersonalGranted = packageManager.checkPermission(PERMISSION_PERSONAL, packageInfo.packageName) == PERMISSION_GRANTED
				val appInfo = AddonAppInfo(name, icon, packageInfo.packageName).also {
					it.intentOpen = resolveIntent(Intent(Intent.ACTION_MAIN).setPackage(packageInfo.packageName))
					it.intentSettings = resolveIntent(Intent(ACTION_APPLICATION_PREFERENCES).setPackage(packageInfo.packageName))
					it.intentDataService = Intent(INTENT_DATA_SERVICE).setPackage(packageInfo.packageName)
					it.cdsNormalRequested = cdsNormalRequested
					it.cdsNormalGranted = cdsNormalGranted
					it.cdsPersonalRequested = cdsPersonalRequested
					it.cdsPersonalGranted = cdsPersonalGranted
				}
				discovered[packageInfo.packageName] = appInfo
			}
		}

		val intentCarService = Intent(INTENT_CONNECTION_SERVICE)
		packageManager.queryIntentServices(intentCarService, 0).forEach { resolveInfo ->
			val packageInfo = packageManager.getPackageInfo(resolveInfo.serviceInfo.packageName, 0)
			val name = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString()
			val icon = packageInfo.applicationInfo.loadIcon(packageManager)
			val appInfo = discovered[packageInfo.packageName] ?: AddonAppInfo(name, icon, packageInfo.packageName).also {
				it.intentOpen = resolveIntent(Intent(Intent.ACTION_MAIN).setPackage(packageInfo.packageName))
				it.intentSettings = resolveIntent(Intent(ACTION_APPLICATION_PREFERENCES).setPackage(packageInfo.packageName))
				it.intentConnectionService = Intent(INTENT_CONNECTION_SERVICE).setPackage(packageInfo.packageName)
				// a direct car connection can provide all the same
				it.cdsNormalRequested = true
				it.cdsNormalGranted = true
				it.cdsPersonalRequested = true
				it.cdsPersonalGranted = true
				// show the checkboxes about the car connection
				it.carConnectionRequested = true
				it.carConnectionGranted = true     // TODO support disabling like the MusicApp swiping
			}
			discovered[packageInfo.packageName] = appInfo
		}

		val results = ArrayList(discovered.values)
		results.sortBy { it.name }

		return results
	}
}