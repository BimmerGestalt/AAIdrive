package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_notification_settings.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.visible

class NotificationSettingsFragment: Fragment() {

	val appSettings by lazy { MutableAppSettingsReceiver(requireContext()) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_notification_settings, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		swNotificationPopup.setOnCheckedChangeListener { buttonView, isChecked ->
			appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP] = isChecked.toString()
			redraw()
		}
		swNotificationPopupPassenger.setOnCheckedChangeListener { buttonView, isChecked ->
			appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER] = isChecked.toString()
		}
		swNotificationSound.setOnCheckedChangeListener { _, isChecked ->
			appSettings[AppSettings.KEYS.NOTIFICATIONS_SOUND] = isChecked.toString()
		}
		swNotificationReadout.setOnCheckedChangeListener { buttonView, isChecked ->
			appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT] = isChecked.toString()
			redraw()
		}
		swNotificationReadoutPopup.setOnCheckedChangeListener { buttonView, isChecked ->
			appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP] = isChecked.toString()
		}
		swNotificationReadoutPopupPassenger.setOnCheckedChangeListener { buttonView, isChecked ->
			appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER] = isChecked.toString()
		}
	}

	override fun onResume() {
		super.onResume()

		redraw()
		appSettings.callback = { redraw() }
	}

	override fun onPause() {
		super.onPause()
		appSettings.callback = null
	}

	fun redraw() {
		if (!isVisible) return
		swNotificationPopup.isChecked = appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP].toBoolean()
		paneNotificationPopup.visible = appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP].toBoolean()
		swNotificationPopupPassenger.isChecked = appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER].toBoolean()
		swNotificationSound.isChecked = appSettings[AppSettings.KEYS.NOTIFICATIONS_SOUND].toBoolean()
		swNotificationReadout.isChecked = appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT].toBoolean()
		swNotificationReadoutPopup.isChecked = appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP].toBoolean()
		paneNotificationReadout.visible = appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP].toBoolean()
		swNotificationReadoutPopupPassenger.isChecked = appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER].toBoolean()
	}
}