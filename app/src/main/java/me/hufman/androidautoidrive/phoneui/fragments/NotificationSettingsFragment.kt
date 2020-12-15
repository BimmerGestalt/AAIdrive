package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import kotlinx.android.synthetic.main.fragment_notification_settings.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BooleanLiveSetting
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.visible

class NotificationSettingsFragment: Fragment() {

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_notification_settings, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val bindings = mapOf(
				swNotificationPopup to AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP,
				swNotificationPopupPassenger to AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER,
				swNotificationSound to AppSettings.KEYS.NOTIFICATIONS_SOUND,
				swNotificationReadout to AppSettings.KEYS.NOTIFICATIONS_READOUT,
				swNotificationReadoutPopup to AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP,
				swNotificationReadoutPopupPassenger to AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER
		)
		bindings.forEach { pair ->
			val switch = pair.key
			val setting = BooleanLiveSetting(requireContext(), pair.value)
			setting.observe(viewLifecycleOwner, Observer {
				switch.isChecked = it
				if (pair.value == AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP) paneNotificationPopup.visible = it
				if (pair.value == AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP) paneNotificationReadout.visible = it
			})
			switch.setOnCheckedChangeListener { _, isChecked ->
				setting.setValue(isChecked)
			}
		}
	}
}