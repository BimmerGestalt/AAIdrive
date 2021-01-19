package me.hufman.androidautoidrive.phoneui

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.gson.JsonObject
import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.CDSDataProvider
import me.hufman.androidautoidrive.phoneui.viewmodels.LanguageSettingsModel
import me.hufman.idriveconnectionkit.CDS
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.*

class LanguageSettingsModelTest {
	@Rule
	@JvmField
	var instantTaskExecutorRule = InstantTaskExecutorRule()

	@Test
	fun testFactory() {
		val context = mock<Context>()
		val factory = LanguageSettingsModel.Factory(context)
		val model = factory.create(LanguageSettingsModel::class.java)
		Assert.assertNotNull(model)
	}

	@Test
	fun testSettings() {
		val context = mock<Context>()
		val cdsData = CDSDataProvider()
		val carInformation = mock<CarInformation>()
		whenever(carInformation.cdsData) doReturn cdsData
		whenever(carInformation.cachedCdsData) doReturn cdsData
		val model = LanguageSettingsModel(context, carInformation)

		AppSettings.loadDefaultSettings()
		val bindings = mapOf(
			model.showAdvanced to AppSettings.KEYS.SHOW_ADVANCED_SETTINGS,
			model.preferCarLanguage to AppSettings.KEYS.PREFER_CAR_LANGUAGE,
		)
		bindings.forEach { (viewModel, setting) ->
			AppSettings.tempSetSetting(setting, "true")
			assertEquals("$setting is true", true, viewModel.value)
			AppSettings.tempSetSetting(setting, "false")
			assertEquals("$setting is false", false, viewModel.value)
		}
		AppSettings.tempSetSetting(AppSettings.KEYS.FORCE_CAR_LANGUAGE, "eo")
		assertEquals("eo", model.forceCarLanguage.value)
	}

	@Test
	fun testCarLanguage() {
		val context = mock<Context>()
		val cdsData = CDSDataProvider()
		val carInformation = mock<CarInformation>()
		whenever(carInformation.cdsData) doReturn cdsData
		whenever(carInformation.cachedCdsData) doReturn cdsData
		val model = LanguageSettingsModel(context, carInformation)

		// test unknown language
		model.lblPreferCarLanguage.observeForever {}
		assertEquals(null, model.carLocale.value)
		context.run(model.lblPreferCarLanguage.value!!)
		verify(context).getString(R.string.lbl_language_prefercar)

		// now set a language
		cdsData.onPropertyChangedEvent(CDS.VEHICLE.LANGUAGE, JsonObject().apply { addProperty("language", 5) })
		assertEquals(Locale("IT"), model.carLocale.value!!)
		context.run(model.lblPreferCarLanguage.value!!)
		verify(context).getString(R.string.lbl_language_prefercar_code, "it")
	}
}