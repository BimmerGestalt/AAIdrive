package me.hufman.androidautoidrive

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
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
				model.mapInvertZoom to AppSettings.KEYS.MAP_INVERT_SCROLL
		)
		bindings.forEach { (viewModel, setting) ->
			AppSettings.tempSetSetting(setting, "true")
			assertEquals("$setting is true", true, viewModel.value)
			AppSettings.tempSetSetting(setting, "false")
			assertEquals("$setting is false", false, viewModel.value)
		}

		assertEquals(false, model.mapWidescreenSupported)
		assertEquals(false, model.mapWidescreenUnsupported)
		assertEquals(false, model.mapWidescreenCrashes)

		whenever(carInformation.mapWidescreenSupported) doReturn true
		whenever(carInformation.mapWidescreenCrashes) doReturn true
		assertEquals(true, model.mapWidescreenSupported)
		assertEquals(false, model.mapWidescreenUnsupported)
		assertEquals(true, model.mapWidescreenCrashes)

		whenever(carInformation.mapWidescreenSupported) doReturn true
		assertEquals(false, model.mapWidescreenSupported)
		assertEquals(true, model.mapWidescreenUnsupported)
		assertEquals(true, model.mapWidescreenCrashes)
	}
}