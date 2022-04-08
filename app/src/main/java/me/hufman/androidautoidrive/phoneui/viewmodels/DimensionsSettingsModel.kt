package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import androidx.lifecycle.*
import io.bimmergestalt.idriveconnectkit.RHMIDimensions
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.map

class DimensionsSettingsModel(appContext: Context, val carCapabilities: LiveData<Map<String, String>>): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			val carCapabilities = MutableLiveData<Map<String, String>>()
			val carInfo = CarInformationObserver { capabilities ->
				carCapabilities.postValue(capabilities)
			}
			if (carInfo.capabilities.isNotEmpty()) {
				carCapabilities.value = carInfo.capabilities
			}
			return DimensionsSettingsModel(appContext, carCapabilities) as T
		}
	}

	private val origDimensions = carCapabilities.map { RHMIDimensions.create(it) }
	val origRhmiWidth = origDimensions.map("") {it.rhmiWidth.toString()}
	val origRhmiHeight = origDimensions.map("") {it.rhmiHeight.toString()}
	val origMarginLeft = origDimensions.map("") {it.marginLeft.toString()}
	val origMarginRight = origDimensions.map("") {it.marginRight.toString()}
	val origPaddingLeft = origDimensions.map("") {it.paddingLeft.toString()}
	val origPaddingTop = origDimensions.map("") {it.paddingTop.toString()}

	val rhmiWidth = StringLiveSetting(appContext, AppSettings.KEYS.DIMENSIONS_RHMI_WIDTH)
	val rhmiHeight = StringLiveSetting(appContext, AppSettings.KEYS.DIMENSIONS_RHMI_HEIGHT)
	val marginLeft = StringLiveSetting(appContext, AppSettings.KEYS.DIMENSIONS_MARGIN_LEFT)
	val marginRight = StringLiveSetting(appContext, AppSettings.KEYS.DIMENSIONS_MARGIN_RIGHT)
	val paddingLeft = StringLiveSetting(appContext, AppSettings.KEYS.DIMENSIONS_PADDING_LEFT)
	val paddingTop = StringLiveSetting(appContext, AppSettings.KEYS.DIMENSIONS_PADDING_TOP)
}