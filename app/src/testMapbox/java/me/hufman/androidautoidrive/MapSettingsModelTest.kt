package me.hufman.androidautoidrive

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import me.hufman.androidautoidrive.phoneui.viewmodels.MapSettingsModel
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MapSettingsModelTest {
	@Rule
	@JvmField
	val instantTaskExecutorRule = InstantTaskExecutorRule()

	@Test
	fun testFactory() {
		val context = mock<Context>()
		val factory = MapSettingsModel.Factory(context)
		val model = factory.create(MapSettingsModel::class.java)
		Assert.assertNotNull(model)
		factory.unsubscribe()
	}

	@Test
	fun testSettings() {
		val context = mock<Context>()
		val carInformation = mock<CarCapabilitiesSummarized>()
		val carInformationLiveData = MutableLiveData(carInformation)
		val model = MapSettingsModel(context, carInformationLiveData)

		AppSettings.loadDefaultSettings()
		val bindings = mapOf(
				model.mapEnabled to AppSettings.KEYS.ENABLED_MAPS,
				model.mapWidescreen to AppSettings.KEYS.MAP_WIDESCREEN,
				model.mapInvertZoom to AppSettings.KEYS.MAP_INVERT_SCROLL
		)
		bindings.forEach { (viewModel, setting) ->
			AppSettings.tempSetSetting(setting, "true")
			assertEquals("$setting is true", true, viewModel.value)
			AppSettings.tempSetSetting(setting, "false")
			assertEquals("$setting is false", false, viewModel.value)
		}

		// make the LiveData start working
		val liveDataObserver = Observer<Boolean> {}
		model.mapWidescreenSupported.observeForever(liveDataObserver)
		model.mapWidescreenUnsupported.observeForever(liveDataObserver)
		model.mapWidescreenCrashes.observeForever(liveDataObserver)

		assertEquals(false, model.mapWidescreenSupported.value)
		assertEquals(false, model.mapWidescreenUnsupported.value)
		assertEquals(false, model.mapWidescreenCrashes.value)

		whenever(carInformation.mapWidescreenSupported) doReturn true
		whenever(carInformation.mapWidescreenUnsupported) doReturn false
		whenever(carInformation.mapWidescreenCrashes) doReturn true
		carInformationLiveData.value = carInformation
		assertEquals(true, model.mapWidescreenSupported.value)
		assertEquals(false, model.mapWidescreenUnsupported.value)
		assertEquals(true, model.mapWidescreenCrashes.value)

		whenever(carInformation.mapWidescreenSupported) doReturn false
		whenever(carInformation.mapWidescreenUnsupported) doReturn true
		carInformationLiveData.value = carInformation
		assertEquals(false, model.mapWidescreenSupported.value)
		assertEquals(true, model.mapWidescreenUnsupported.value)
		assertEquals(true, model.mapWidescreenCrashes.value)

		model.mapWidescreenSupported.removeObserver(liveDataObserver)
		model.mapWidescreenUnsupported.removeObserver(liveDataObserver)
		model.mapWidescreenCrashes.removeObserver(liveDataObserver)
	}
}