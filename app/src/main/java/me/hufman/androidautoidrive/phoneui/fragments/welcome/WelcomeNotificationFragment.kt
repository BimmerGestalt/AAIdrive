package me.hufman.androidautoidrive.phoneui.fragments.welcome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_notificationpage.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BooleanLiveSetting
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.controllers.PermissionsController
import me.hufman.androidautoidrive.phoneui.viewmodels.PermissionsModel
import me.hufman.androidautoidrive.phoneui.ViewHelpers.visible

class WelcomeNotificationFragment: Fragment() {
	val permissionsController by lazy { PermissionsController(requireActivity()) }
	val viewModel by lazy { PermissionsModel.Factory(requireContext().applicationContext).create(PermissionsModel::class.java) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_welcome_notification, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val notificationsEnabledSetting = BooleanLiveSetting(requireContext().applicationContext, AppSettings.KEYS.ENABLED_NOTIFICATIONS)
		notificationsEnabledSetting.observe(viewLifecycleOwner) {
			swMessageNotifications.isChecked = it
			paneNotificationSettings.visible = it
		}
		swMessageNotifications.setOnCheckedChangeListener { _, isChecked ->
			onChangedSwitchNotifications(notificationsEnabledSetting, isChecked)
		}
	}

	private fun onChangedSwitchNotifications(appSetting: BooleanLiveSetting, isChecked: Boolean) {
		appSetting.setValue(isChecked)
		if (isChecked) {
			// make sure we have permissions to read the notifications
			if (viewModel.hasNotificationPermission.value != true) {
				permissionsController.promptNotification()
			}
		}
	}

}