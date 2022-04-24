package me.hufman.androidautoidrive

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import me.hufman.androidautoidrive.phoneui.viewmodels.MapSettingsModel
import org.junit.Assert
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
	}

	@Test
	fun testSettings() {
		val context = mock<Context>()
		val carInformation = mock<CarCapabilitiesSummarized>()
		val model = MapSettingsModel(context, MutableLiveData(carInformation))

		AppSettings.loadDefaultSettings()
		val bindings = mapOf(
				model.mapEnabled to AppSettings.KEYS.ENABLED_MAPS,
				model.mapWidescreen to AppSettings.KEYS.MAP_WIDESCREEN,
				model.mapInvertZoom to AppSettings.KEYS.MAP_INVERT_SCROLL,
				model.mapTraffic to AppSettings.KEYS.MAP_TRAFFIC,
				model.mapBuildings to AppSettings.KEYS.MAP_BUILDINGS
		)
		bindings.forEach { (viewModel, setting) ->
			AppSettings.tempSetSetting(setting, "true")
			Assert.assertEquals("$setting is true", true, viewModel.value)
			AppSettings.tempSetSetting(setting, "false")
			Assert.assertEquals("$setting is false", false, viewModel.value)
		}
		AppSettings.tempSetSetting(AppSettings.KEYS.GMAPS_STYLE, "night1")
		Assert.assertEquals("night1", model.mapStyle.value)

		Assert.assertEquals(false, model.mapWidescreenSupported)
		Assert.assertEquals(false, model.mapWidescreenUnsupported)
		Assert.assertEquals(false, model.mapWidescreenCrashes)

		whenever(carInformation.mapWidescreenSupported) doReturn true
		whenever(carInformation.mapWidescreenCrashes) doReturn true
		Assert.assertEquals(true, model.mapWidescreenSupported)
		Assert.assertEquals(false, model.mapWidescreenUnsupported)
		Assert.assertEquals(true, model.mapWidescreenCrashes)

		whenever(carInformation.mapWidescreenSupported) doReturn true
		Assert.assertEquals(false, model.mapWidescreenSupported)
		Assert.assertEquals(true, model.mapWidescreenUnsupported)
		Assert.assertEquals(true, model.mapWidescreenCrashes)
	}
}