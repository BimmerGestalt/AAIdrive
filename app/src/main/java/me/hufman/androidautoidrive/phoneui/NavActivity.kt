package me.hufman.androidautoidrive.phoneui

import android.app.Activity
import android.util.Log
import me.hufman.androidautoidrive.carapp.NavigationTrigger.Companion.parseGeoUrl
import me.hufman.androidautoidrive.carapp.NavigationTriggerSender

class NavActivity: Activity() {
	companion object {
		val TAG = "NavActivity"
	}

	override fun onStart() {
		super.onStart()
		val intent = intent
		if (intent?.scheme == "geo") {
			val url = intent.data
			if (url != null) {
				val rhmiNavigationData = parseGeoUrl(url.toString())
				Log.i(TAG, "Parsing GEO Uri $url to $rhmiNavigationData initiate IDrive navigation")
				if (rhmiNavigationData != null) {
					NavigationTriggerSender(this).triggerNavigation(rhmiNavigationData)
				}
			}

		}
		finish()
	}
}