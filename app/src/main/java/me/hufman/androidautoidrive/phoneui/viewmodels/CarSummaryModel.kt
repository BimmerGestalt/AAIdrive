package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.map

class CarSummaryModel(carInfoOverride: CarInformation? = null, val showAdvancedSettings: BooleanLiveSetting): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			val handler = Handler(Looper.getMainLooper())
			var model: CarSummaryModel? = null
			val carInfo = CarInformationObserver {
				handler.post { model?.update() }
			}
			model = CarSummaryModel(carInfo, BooleanLiveSetting(appContext, AppSettings.KEYS.SHOW_ADVANCED_SETTINGS))
			model.update()
			return model as T
		}
	}

	private val carInfo = carInfoOverride ?: CarInformation()

	private val _carBrand = MutableLiveData<String?>(null)
	val carBrand: LiveData<String?> = _carBrand
	private val _carLogo = MutableLiveData<Context.() -> Drawable?> { null }
	val carLogo: LiveData<Context.() -> Drawable?> = _carLogo
	private val _carChassisCode = MutableLiveData<ChassisCode?>()
	val carChassisCode = _carChassisCode

	private val hmiVersionMatcher = Regex("^[A-Za-z0-9_]*(?=_[0-9])")   // look for the first numeric segment and split before
	private val hmiMatcher = Regex("^[A-Za-z0-9]*(?=_)")        // fallback to split on the first underscore
	private val _hmiVersion = MutableLiveData<String>(null)
	val hmiVersion: LiveData<String> = _hmiVersion

	val hasConnected = _carBrand.map(false) { true }

	fun update() {
		// current car overview
		val brand = carInfo.capabilities["hmi.type"]?.split(' ')?.first()
		_carBrand.value = brand
		when (brand) {
			"BMW" -> _carLogo.value = { ContextCompat.getDrawable(this, R.drawable.logo_bmw) }
			"MINI" -> _carLogo.value = { ContextCompat.getDrawable(this, R.drawable.logo_mini) }
			else -> _carLogo.value = { null }
		}

		val chassisCode = ChassisCode.fromCode(carInfo.capabilities["vehicle.type"] ?: "Unknown")
		_carChassisCode.value = chassisCode

		val hmiType = carInfo.capabilities["hmi.type"] ?: ""
		val hmiVersion = carInfo.capabilities["hmi.version"] ?: ""
		val mainHmiVersion = (hmiVersionMatcher.find(hmiVersion) ?: hmiMatcher.find(hmiVersion))?.value ?: ""
		// recent ID5/6 versions just say EntryEvo and don't have a specific category
		val displayedHmiVersion = if (mainHmiVersion.contains("EntryEvo_ID5")) hmiType else mainHmiVersion
		_hmiVersion.value = displayedHmiVersion
	}
}