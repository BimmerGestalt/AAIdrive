package me.hufman.androidautoidrive.phoneui

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockito_kotlin.mock
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.phoneui.viewmodels.NotificationSettingsModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NotificationSettingsModelTest {
	@Rule
	@JvmField
	val instantTaskExecutorRule = InstantTaskExecutorRule()

	@Test
	fun testFactory() {
		val context = mock<Context>()
		val factory = NotificationSettingsModel.Factory(context)
		val model = factory.create(NotificationSettingsModel::class.java)
		assertNotNull(model)
	}

	@Test
	fun testSettings() {
		val context = mock<Context>()
		val model = NotificationSettingsModel(context)

		AppSettings.loadDefaultSettings()
		val bindings = mapOf(
				model.notificationEnabled to AppSettings.KEYS.ENABLED_NOTIFICATIONS,
				model.notificationPopup to AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP,
				model.notificationPopupPassenger to AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER,
				model.notificationSound to AppSettings.KEYS.NOTIFICATIONS_SOUND,
				model.notificationReadout to AppSettings.KEYS.NOTIFICATIONS_READOUT,
				model.notificationReadoutPopup to AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP,
				model.notificationReadoutPopupPassenger to AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER
		)
		bindings.forEach { (viewModel, setting) ->
			AppSettings.tempSetSetting(setting, "true")
			assertEquals("$setting is true", true, viewModel.value)
			AppSettings.tempSetSetting(setting, "false")
			assertEquals("$setting is false" , false, viewModel.value)
		}
	}
}