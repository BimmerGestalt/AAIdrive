package me.hufman.androidautoidrive.phoneui

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockito_kotlin.mock
import me.hufman.androidautoidrive.AppSettings
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
		val model = MapSettingsModel(context)

		AppSettings.loadDefaultSettings()
		val bindings = mapOf(
				model.mapEnabled to AppSettings.KEYS.ENABLED_GMAPS,
				model.mapWidescreen to AppSettings.KEYS.MAP_WIDESCREEN,
				model.mapInvertZoom to AppSettings.KEYS.MAP_INVERT_SCROLL,
				model.mapTraffic to AppSettings.KEYS.MAP_TRAFFIC,
				model.gmapBuildings to AppSettings.KEYS.GMAPS_BUILDINGS
		)
		bindings.forEach { (viewModel, setting) ->
			AppSettings.tempSetSetting(setting, "true")
			Assert.assertEquals("$setting is true", true, viewModel.value)
			AppSettings.tempSetSetting(setting, "false")
			Assert.assertEquals("$setting is false", false, viewModel.value)
		}
		AppSettings.tempSetSetting(AppSettings.KEYS.GMAPS_STYLE, "night1")
		Assert.assertEquals("night1", model.mapStyle.value)
	}
}