package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.addons.AddonAppInfo
import me.hufman.androidautoidrive.addons.AddonDiscovery

class AddonsViewModel(val discovery: AddonDiscovery): ViewModel() {
	class Factory(val context: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel?> create(modelClass: Class<T>): T {
			val addonDiscovery = AddonDiscovery(context.applicationContext.packageManager)
			val viewModel: AddonsViewModel? = AddonsViewModel(addonDiscovery)
			return viewModel as T
		}
	}

	val apps = ArrayList<AddonAppInfo>()

	fun update() {
		apps.clear()
		apps.addAll(discovery.discoverApps())
	}
}