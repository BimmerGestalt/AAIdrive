package me.hufman.androidautoidrive.phoneui

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockito_kotlin.mock
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicAdvancedSettingsModel
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class MusicAdvancedSettingsModelTest {
	@Rule
	@JvmField
	val instantTaskExecutorRule = InstantTaskExecutorRule()

	@Test
	fun testFactory() {
		val context = mock<Context>()
		val factory = MusicAdvancedSettingsModel.Factory(context)
		val model = factory.create(MusicAdvancedSettingsModel::class.java)
		Assert.assertNotNull(model)
	}

	@Test
	fun testSettings() {
		val context = mock<Context>()
		val model = MusicAdvancedSettingsModel(context)

		AppSettings.loadDefaultSettings()
		val bindings = mapOf(
				model.showAdvanced to AppSettings.KEYS.SHOW_ADVANCED_SETTINGS,
				model.audioContext to AppSettings.KEYS.AUDIO_FORCE_CONTEXT,
				model.spotifyLayout to AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT
		)
		bindings.forEach { (viewModel, setting) ->
			AppSettings.tempSetSetting(setting, "true")
			Assert.assertEquals("$setting is true", true, viewModel.value)
			AppSettings.tempSetSetting(setting, "false")
			Assert.assertEquals("$setting is false", false, viewModel.value)
		}
	}
}