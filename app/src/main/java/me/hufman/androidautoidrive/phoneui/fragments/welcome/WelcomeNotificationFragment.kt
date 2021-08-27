package me.hufman.androidautoidrive.phoneui.fragments.welcome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.databinding.WelcomeNotificationBinding
import me.hufman.androidautoidrive.phoneui.controllers.NotificationPageController
import me.hufman.androidautoidrive.phoneui.controllers.PermissionsController
import me.hufman.androidautoidrive.phoneui.viewmodels.PermissionsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.NotificationSettingsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.viewModels

class WelcomeNotificationFragment: Fragment() {
	val notificationSettingsModel by viewModels<NotificationSettingsModel> { NotificationSettingsModel.Factory(requireContext().applicationContext)}
	val permissionsModel by viewModels<PermissionsModel> {PermissionsModel.Factory(requireContext().applicationContext)}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val permissionsController by lazy { PermissionsController(requireActivity()) }
		val notificationPageController by lazy { NotificationPageController(notificationSettingsModel, permissionsModel, permissionsController) }

		val binding = WelcomeNotificationBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.settingsModel = notificationSettingsModel
		binding.controller = notificationPageController
		return binding.root
	}
}