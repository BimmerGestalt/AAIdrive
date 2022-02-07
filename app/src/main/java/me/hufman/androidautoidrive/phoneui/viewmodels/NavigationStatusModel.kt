package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.bimmergestalt.idriveconnectkit.CDS
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.CDSVehicleUnits
import me.hufman.androidautoidrive.carapp.liveData
import me.hufman.androidautoidrive.maps.MapResult
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.combine
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.map
import java.util.*

class NavigationStatusModel(val carInformation: CarInformation,
                            var isCustomNaviSupported: LiveData<Boolean>, var isCustomNaviPreferred: MutableLiveData<Boolean>): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			val postHandler = Handler(Looper.getMainLooper())
			var viewModel: NavigationStatusModel? = null
			val carInformation = CarInformationObserver {
				// update the detected capabilities
				postHandler.post {
					viewModel?.update()
				}
			}
			val isCustomNaviSupported = MutableLiveData(BuildConfig.FLAVOR_map == "gmap" || BuildConfig.FLAVOR_map == "mapbox")
			val isCustomNaviPreferred = BooleanLiveSetting(appContext, AppSettings.KEYS.NAV_PREFER_CUSTOM_MAP)
			viewModel = NavigationStatusModel(carInformation, isCustomNaviSupported, isCustomNaviPreferred)
			viewModel.update()
			return viewModel as T
		}
	}

	val isCustomNaviSupportedAndPreferred = isCustomNaviSupported.combine(isCustomNaviPreferred) { supported, preferred ->
		supported && preferred
	}
	private val _isCarNaviSupported = MutableLiveData(false)
	val isCarNaviSupported: LiveData<Boolean> = _isCarNaviSupported
	private val _isCarNaviNotSupported = MutableLiveData(false)
	val isCarNaviNotSupported: LiveData<Boolean> = _isCarNaviNotSupported
	val isNaviNotSupported = isCarNaviNotSupported.combine(isCustomNaviSupportedAndPreferred) {carNot, custom ->
		carNot && !custom
	}

	// autocomplete results
	val query = MutableLiveData("")
	val autocompleteResults = ArrayList<MapResultViewModel>()

	// progress as we are searching and starting navigation
	private val _isConnected = MutableLiveData(false)
	val isConnected: LiveData<Boolean> = _isConnected
	val isSearching = MutableLiveData(false)
	val searchStatus = MutableLiveData<Context.() -> String> { "" }
	val searchFailed = MutableLiveData(false)

	val isNavigating = carInformation.cdsData.liveData[CDS.NAVIGATION.GUIDANCESTATUS].map(false) {
		it["guidanceStatus"]?.asInt == 1
	}
	val navigationStatus: LiveData<Context.() -> String> = isNavigating.map({ getString(R.string.lbl_navigationstatus_inactive) }) { isNavigating ->
		if (isNavigating) {
			{ getString(R.string.lbl_navigationstatus_active) }
		} else {
			{ getString(R.string.lbl_navigationstatus_inactive) }
		}
	}
	val destination = carInformation.cdsData.liveData[CDS.NAVIGATION.NEXTDESTINATION].map("") {
		it["nextDestination"]?.asJsonObject?.getAsJsonPrimitive("name")?.asString
	}

	fun update() {
		_isConnected.value = carInformation.isConnected

		val capabilities = carInformation.capabilities
		_isCarNaviSupported.value = capabilities["navi"]?.lowercase(Locale.ROOT) == "true"
		_isCarNaviNotSupported.value = capabilities["navi"]?.lowercase(Locale.ROOT) == "false"
	}
}

class MapResultViewModel(val carInfo: CarInformation, val result: MapResult) {
	val units: CDSVehicleUnits = carInfo.cachedCdsData[CDS.VEHICLE.UNITS]?.let {
		CDSVehicleUnits.fromCdsProperty(it)
	} ?: CDSVehicleUnits.UNKNOWN
	val unitsDistanceLabel: Context.() -> String = units.let {
		when (it.distanceUnits) {
			CDSVehicleUnits.Distance.Kilometers -> {{ getString(R.string.lbl_carinfo_units_km) }}
			CDSVehicleUnits.Distance.Miles -> {{ getString(R.string.lbl_carinfo_units_mi) }}
		}
	}

	val name = result.name
	val address = result.address
	val distance = result.distanceKm?.let {
		val unitDistance = units.distanceUnits.fromCarUnit(it)
		if (unitDistance < 10.0) {
			String.format("%.1f", unitDistance)
		} else {
			String.format("%.0f", unitDistance)
		}
	}

	override fun toString(): String {
		return result.toString()
	}


}