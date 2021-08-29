package me.hufman.androidautoidrive.addons

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import me.hufman.androidautoidrive.R

data class AddonAppInfo(val name: String, val icon: Drawable, val packageName: String) {
	var intentOpen: Intent? = null
	var intentSettings: Intent? = null

	var cdsNormalRequested: Boolean = false
	var cdsNormalGranted: Boolean = false
	var cdsPersonalRequested: Boolean = false
	var cdsPersonalGranted: Boolean = false

	fun featuresString(): Context.() -> String {
		val appInfo = this
		return {
			listOfNotNull(
				if (appInfo.cdsNormalRequested) {
					if (appInfo.cdsNormalGranted) getString(R.string.addonNormalPermissionGranted) else getString(R.string.addonNormalPermissionDeclined)
				} else null,
				if (appInfo.cdsPersonalRequested) {
					if (appInfo.cdsPersonalGranted) getString(R.string.addonPersonalPermissionGranted) else getString(R.string.addonPersonalPermissionDeclined)
				} else null
			).joinToString("\n")
		}
	}
}
