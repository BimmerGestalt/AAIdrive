package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BooleanLiveSetting

class NotificationSettingsModel(appContext: Context): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return NotificationSettingsModel(appContext) as T
		}
	}

	val notificationEnabled = BooleanLiveSetting(appContext, AppSettings.KEYS.ENABLED_NOTIFICATIONS)
	val notificationPopup = BooleanLiveSetting(appContext, AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP)
	val notificationPopupPassenger = BooleanLiveSetting(appContext, AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER)
	val notificationSound = BooleanLiveSetting(appContext, AppSettings.KEYS.NOTIFICATIONS_SOUND)
	val notificationReadout = BooleanLiveSetting(appContext, AppSettings.KEYS.NOTIFICATIONS_READOUT)
	val notificationReadoutPopup = BooleanLiveSetting(appContext, AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP)
	val notificationReadoutPopupPassenger = BooleanLiveSetting(appContext, AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER)
}