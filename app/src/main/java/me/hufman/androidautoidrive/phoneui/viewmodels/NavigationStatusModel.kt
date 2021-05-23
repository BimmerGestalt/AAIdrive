package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.os.Handler
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.CarInformationObserver
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.liveData
import me.hufman.androidautoidrive.phoneui.LiveDateHelpers.map
import me.hufman.idriveconnectionkit.CDS
import java.util.*

class NavigationStatusModel(val carInformation: CarInformation): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			val postHandler = Handler()
			var viewModel: NavigationStatusModel? = null
			val carInformation = CarInformationObserver {
				// update the detected capabilities
				postHandler.post {
					viewModel?.update()
				}
			}
			viewModel = NavigationStatusModel(carInformation)
			viewModel.update()
			return viewModel as T
		}
	}

	private val _isNaviSupported = MutableLiveData<Boolean>(false)
	val isNaviSupported: LiveData<Boolean> = _isNaviSupported
	private val _isNaviNotSupported = MutableLiveData<Boolean>()
	val isNaviNotSupported: LiveData<Boolean> = _isNaviNotSupported

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
		_isNaviSupported.value = capabilities["navi"]?.toLowerCase(Locale.ROOT) == "true"
		_isNaviNotSupported.value = capabilities["navi"]?.toLowerCase(Locale.ROOT) == "false"
	}
}