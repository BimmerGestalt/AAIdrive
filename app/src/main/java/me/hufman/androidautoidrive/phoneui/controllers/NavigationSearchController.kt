package me.hufman.androidautoidrive.phoneui.controllers

import androidx.lifecycle.Observer
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.DefaultDispatcherProvider
import me.hufman.androidautoidrive.DispatcherProvider
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.navigation.NavigationParser
import me.hufman.androidautoidrive.carapp.navigation.NavigationTrigger
import me.hufman.androidautoidrive.phoneui.viewmodels.NavigationStatusModel
import java.net.URLEncoder

class NavigationSearchController(val scope: CoroutineScope, val parser: NavigationParser, val navigationTrigger: NavigationTrigger, val navigationStatusModel: NavigationStatusModel, val dispatchers: DispatcherProvider = DefaultDispatcherProvider()) {
	var job: Job? = null

	companion object {
		const val TRIES = 3
		const val TIMEOUT = 5000L
		const val SUCCESS = 3000L
	}

	var query: String = ""
		set(value) {
			navigationStatusModel.searchFailed.value = false
			field = value
		}

	fun startNavigation() {
		startNavigation(query)
	}

	fun startNavigation(query: CharSequence): Boolean {
		job?.cancel()   // cancel a previous search or pending state
		job = scope.launch(dispatchers.Main) {
			navigationStatusModel.isSearching.value = true
			navigationStatusModel.searchStatus.value = { getString(R.string.lbl_navigation_listener_searching) }
			navigationStatusModel.searchFailed.value = false
			val result = searchToRHMI(query)

			// start navigation in the car
			if (result == null) {
				navigationStatusModel.searchStatus.value = { getString(R.string.lbl_navigation_listener_parsefailure) }
				navigationStatusModel.searchFailed.value = true
			} else {
				navigationStatusModel.searchStatus.value = { getString(R.string.lbl_navigation_listener_pending) }

				// trigger navigation with the discovered result
				triggerNavigation(result)

				// update the result label
				if (navigationStatusModel.isNavigating.value == true) {
					navigationStatusModel.searchStatus.value = { getString(R.string.lbl_navigation_listener_success) }
				} else {
					navigationStatusModel.searchStatus.value = { getString(R.string.lbl_navigation_listener_unsuccess) }
				}
			}

			// clear the progress text
			navigationStatusModel.isSearching.value = false
			delay(SUCCESS)
			navigationStatusModel.searchStatus.value = { "" }
		}
		return false    // hide the keyboard after clicking the search button
	}

	suspend fun searchToRHMI(query: CharSequence): String? {
		val url = if (query.startsWith("geo:") ||
				query.startsWith("google.navigation:") ||
				query.startsWith("http")) {
			query
		} else {
			"geo:0,0?q=${URLEncoder.encode(query.toString(), "UTF-8")}"
		}

		val result = withContext(dispatchers.IO) {
			parser.parseUrl(url.toString()) ?:
			parser.parseUrl(url.toString()) // try a second time
		}
		return result
	}

	suspend fun triggerNavigation(rhmiDestination: String) {
		val observer = Observer<Boolean> {}
		try {
			// register for navigation status
			navigationStatusModel.isNavigating.observeForever(observer)
			// try a few times
			for (i in 0 until TRIES) {
				withContext(dispatchers.IO) {
					navigationTrigger.triggerNavigation(rhmiDestination)
				}
				for (t in 0 until TIMEOUT / 1000) {
					delay(1000)
					// wait up to TIMEOUT or until car begins navigation
					if (navigationStatusModel.isNavigating.value == true) {
						break
					}
				}
				// if the car is navigating, don't try again
				if (navigationStatusModel.isNavigating.value == true) {
					break
				}
			}
		} finally {
			navigationStatusModel.isNavigating.removeObserver(observer)
		}
	}
}