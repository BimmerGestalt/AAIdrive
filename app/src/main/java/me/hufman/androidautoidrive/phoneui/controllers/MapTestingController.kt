package me.hufman.androidautoidrive.phoneui.controllers

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.Surface
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.bimmergestalt.idriveconnectkit.CDS
import io.bimmergestalt.idriveconnectkit.CDSProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.hufman.androidautoidrive.DefaultDispatcherProvider
import me.hufman.androidautoidrive.DispatcherProvider
import me.hufman.androidautoidrive.carapp.maps.MapInteractionController
import me.hufman.androidautoidrive.cds.CDSDataProvider
import me.hufman.androidautoidrive.maps.MapPlaceSearch
import me.hufman.androidautoidrive.maps.MapResult
import me.hufman.androidautoidrive.phoneui.viewmodels.MapTestingModel

class MapTestingController(
	val scope: CoroutineScope,
	val viewModel: MapTestingModel,
	val mapInteractionController: MapInteractionController,
	val cdsData: CDSDataProvider,
	val mapPlaceSearch: MapPlaceSearch,
	val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) {
	private val ACTION_START = "me.hufman.androidautoidrive.MapTestingService.start"
	private val ACTION_PAUSE = "me.hufman.androidautoidrive.MapTestingService.pause"
	private val EXTRA_SURFACE = "me.hufman.androidautoidrive.MapTestingService.SURFACE"
	private val EXTRA_WIDTH = "me.hufman.androidautoidrive.MapTestingService.WIDTH"
	private val EXTRA_HEIGHT = "me.hufman.androidautoidrive.MapTestingService.HEIGHT"

	private var locationJob: Job? = null
	private var navigationJob: Job? = null

	fun start(context: Context, surface: Surface, width: Int, height: Int) {
		val intent = Intent(ACTION_START).apply {
			setPackage(context.packageName)
			putExtra(EXTRA_SURFACE, surface)
			putExtra(EXTRA_WIDTH, width)
			putExtra(EXTRA_HEIGHT, height)
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			context.startForegroundService(intent)
		} else {
			context.startService(intent)
		}
	}

	fun pause(context: Context) {
		context.startService(Intent(ACTION_PAUSE).setPackage(context.packageName))
	}

	fun zoomIn(steps: Int = 1) = mapInteractionController.zoomIn(steps)
	fun zoomOut(steps: Int = 1) = mapInteractionController.zoomOut(steps)

	fun moveForward() {
		val currentLocation = cdsData[CDS.NAVIGATION.GPSPOSITION] ?: return
		val currentLatitude = currentLocation.getAsJsonObject("GPSPosition")
			?.getAsJsonPrimitive("latitude")
			?.asDouble ?: return
		val currentLongitude = currentLocation.getAsJsonObject("GPSPosition")
			?.getAsJsonPrimitive("longitude")
			?.asDouble ?: return
		val currentExtendedInfo = cdsData[CDS.NAVIGATION.GPSEXTENDEDINFO]
		val heading = currentExtendedInfo?.getAsJsonObject("GPSExtendedInfo")
			?.getAsJsonPrimitive("heading")
			?.asDouble
			?: 0.0

		// https://stackoverflow.com/a/47572447
		val distance = 100.0
		val headingRads = Math.toRadians(heading + 90)
		val lat0 = Math.cos(Math.PI / 180.0 * currentLatitude)
		val newLatitude = currentLatitude + (180/Math.PI) * (distance / 6378137) * Math.sin(headingRads)
		val newLongitude = currentLongitude + (180/Math.PI) * (distance / 6378137)/Math.cos(lat0) * Math.cos(headingRads)
		cdsData.onPropertyChangedEvent(CDSProperty.NAVIGATION_GPSPOSITION, JsonObject().apply {
			add("GPSPosition", JsonObject().apply {
				add("latitude", JsonPrimitive(newLatitude))
				add("longitude",  JsonPrimitive(newLongitude))
			})
		})
	}

	fun steer(angle: Double) {
		val currentExtendedInfo = cdsData[CDS.NAVIGATION.GPSEXTENDEDINFO]
		val heading = currentExtendedInfo?.getAsJsonObject("GPSExtendedInfo")
			?.getAsJsonPrimitive("heading")
			?.asDouble
			?: 0.0

		val newHeading = heading + angle
		val newHeadingWrapped = if (newHeading < 0) {
			newHeading + 360
		} else if (newHeading > 360) {
			newHeading - 360
		} else {
			newHeading
		}

		cdsData.onPropertyChangedEvent(CDSProperty.NAVIGATION_GPSEXTENDEDINFO, JsonObject().apply {
			add("GPSExtendedInfo", JsonObject().apply {
				add("heading", JsonPrimitive(newHeadingWrapped))
			})
		})
	}

	fun steerLeft() = steer(30.0)
	fun steerRight() = steer(-30.0)

	fun setLocation() {
		setLocation(viewModel.locationQuery.value ?: "")
	}

	fun setLocation(query: CharSequence): Boolean {
		locationJob?.cancel()   // cancel a previous search or pending state
		locationJob = scope.launch(dispatchers.Main) {
			viewModel.isSearchingLocation.value = true
			val results = mapPlaceSearch.searchLocationsAsync(query.toString()).await()
			if (results.isEmpty()) {
				viewModel.isSearchingLocation.value = false
			} else {
				setLocation(results[0])
			}
		}
		return false    // hide the keyboard after clicking the search button
	}

	fun setLocation(result: MapResult) {
		locationJob?.cancel()   // cancel a previous search or pending state
		locationJob = scope.launch(dispatchers.Main) {
			// show that we are searching while resolving the full MapResult
			viewModel.isSearchingLocation.value = true

			val expandedResult = if (result.location != null) {
				result
			} else {
				mapPlaceSearch.resultInformationAsync(result.id).await() ?: result
			}
			if (expandedResult.address != null) {
				viewModel.locationQuery.value = expandedResult.toString()
			}

			viewModel.isSearchingLocation.value = false

			expandedResult.location?.let {
				cdsData.onPropertyChangedEvent(CDSProperty.NAVIGATION_GPSPOSITION, JsonObject().apply {
					add("GPSPosition", JsonObject().apply {
						add("latitude", JsonPrimitive(it.latitude))
						add("longitude",  JsonPrimitive(it.longitude))
					})
				})
			}
		}
	}

	fun startNavigation() {
		startNavigation(viewModel.navigationQuery.value ?: "")
	}

	fun startNavigation(query: CharSequence): Boolean {
		navigationJob?.cancel()   // cancel a previous search or pending state
		navigationJob = scope.launch(dispatchers.Main) {
			viewModel.isSearchingNavigation.value = true
			val results = mapPlaceSearch.searchLocationsAsync(query.toString()).await()
			if (results.isEmpty()) {
				viewModel.isSearchingNavigation.value = false
			} else {
				startNavigation(results[0])
			}
		}
		return false    // hide the keyboard after clicking the search button
	}

	fun startNavigation(result: MapResult) {
		navigationJob?.cancel()   // cancel a previous search or pending state
		navigationJob = scope.launch(dispatchers.Main) {
			// show that we are searching while resolving the full MapResult
			viewModel.isSearchingNavigation.value = true

			val expandedResult = if (result.location != null) {
				result
			} else {
				mapPlaceSearch.resultInformationAsync(result.id).await() ?: result
			}
			if (expandedResult.address != null) {
				viewModel.navigationQuery.value = expandedResult.toString()
			}

			viewModel.isSearchingNavigation.value = false

			expandedResult.location?.let {
				mapInteractionController.navigateTo(it)
			}
		}
	}
}