package me.hufman.androidautoidrive.phoneui.controllers

import android.location.Address
import androidx.lifecycle.Observer
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.DefaultDispatcherProvider
import me.hufman.androidautoidrive.DispatcherProvider
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.navigation.NavigationParser
import me.hufman.androidautoidrive.carapp.navigation.NavigationTrigger
import me.hufman.androidautoidrive.maps.MapPlaceSearch
import me.hufman.androidautoidrive.maps.MapResult
import me.hufman.androidautoidrive.phoneui.viewmodels.NavigationStatusModel
import java.net.URLEncoder

class NavigationSearchController(val scope: CoroutineScope, val parser: NavigationParser, val mapPlaceSearch: MapPlaceSearch,
                                 val navigationTrigger: NavigationTrigger, val navigationStatusModel: NavigationStatusModel,
                                 val dispatchers: DispatcherProvider = DefaultDispatcherProvider()) {
	var job: Job? = null

	companion object {
		const val TRIES = 3
		const val TIMEOUT = 5000L
		const val SUCCESS = 3000L
	}

	fun startNavigation() {
		startNavigation(navigationStatusModel.query.value ?: "")
	}

	@Suppress("BlockingMethodInNonBlockingContext")
	fun startNavigation(result: MapResult) {
		job?.cancel()   // cancel a previous search or pending state
		job = scope.launch(dispatchers.Main) {
			// show that we are searching while resolving the full MapResult
			navigationStatusModel.isSearching.value = true
			navigationStatusModel.searchStatus.value = { getString(R.string.lbl_navigation_listener_searching) }
			navigationStatusModel.searchFailed.value = false

			val expandedResult = if (result.location != null) {
				result
			} else {
				mapPlaceSearch.resultInformationAsync(result.id).await() ?: result
			}
			if (expandedResult.address != null) {
				navigationStatusModel.query.value = expandedResult.toString()
			}

			// convert to a geo: search url for NavigationTrigger
			// Google Place results don't include a location, for some reason
			val query = if (expandedResult.location != null) {
				"geo:0,0?q=${expandedResult.location.latitude},${expandedResult.location.longitude}+%28${URLEncoder.encode(expandedResult.toString(),"UTF-8")}%29"
			} else {
				"geo:0,0?q=${URLEncoder.encode(result.address, "UTF-8")}"
			}
			startNavigationUpdates(query)
		}
	}

	fun startNavigation(query: CharSequence): Boolean {
		job?.cancel()   // cancel a previous search or pending state
		job = scope.launch(dispatchers.Main) {
			startNavigationUpdates(query)
		}
		return false    // hide the keyboard after clicking the search button
	}

	/**
	 * Run the navigation process, and update the status module at different phases
	 */
	private suspend fun startNavigationUpdates(query: CharSequence) {
		navigationStatusModel.isSearching.value = true
		navigationStatusModel.searchStatus.value = { getString(R.string.lbl_navigation_listener_searching) }
		navigationStatusModel.searchFailed.value = false
		val result = searchAddress(query)

		// start navigation in the car
		if (result == null) {
			navigationStatusModel.searchStatus.value = { getString(R.string.lbl_navigation_listener_parsefailure) }
			navigationStatusModel.searchFailed.value = true
		} else {
			navigationStatusModel.searchStatus.value = { getString(R.string.lbl_navigation_listener_pending) }

			// trigger navigation with the discovered result
			triggerNavigation(result)

			// update the result label
			if (navigationStatusModel.isCarNavigating.value == true ||
					navigationStatusModel.isCustomNaviSupportedAndPreferred.value == true) {
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

	suspend fun searchAddress(query: CharSequence): Address? {
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

	suspend fun triggerNavigation(destination: Address) {
		val observer = Observer<Boolean> {}
		try {
			// register for navigation status
			navigationStatusModel.isCarNavigating.observeForever(observer)
			navigationStatusModel.isCustomNaviSupportedAndPreferred.observeForever(observer)
			// try a few times
			for (i in 0 until TRIES) {
				withContext(dispatchers.IO) {
					navigationTrigger.triggerNavigation(destination)
				}
				for (t in 0 until TIMEOUT / 1000) {
					delay(1000)
					// wait up to TIMEOUT or until car begins navigation
					if (navigationStatusModel.isCarNavigating.value == true ||
							navigationStatusModel.isCustomNaviSupportedAndPreferred.value == true) {
						break
					}
				}
				// if the car is navigating, don't try again
				if (navigationStatusModel.isCarNavigating.value == true ||
						navigationStatusModel.isCustomNaviSupportedAndPreferred.value == true) {
					break
				}
			}
		} finally {
			navigationStatusModel.isCarNavigating.removeObserver(observer)
			navigationStatusModel.isCustomNaviSupportedAndPreferred.removeObserver(observer)
		}
	}
}