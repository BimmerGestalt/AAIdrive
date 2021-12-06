package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.databinding.CalendarPageBinding
import me.hufman.androidautoidrive.phoneui.controllers.CalendarPageController
import me.hufman.androidautoidrive.phoneui.controllers.PermissionsController
import me.hufman.androidautoidrive.phoneui.viewmodels.CalendarSettingsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.PermissionsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.viewModels

class CalendarPageFragment: Fragment() {
	val calendarSettingsModel by viewModels<CalendarSettingsModel> { CalendarSettingsModel.Factory(requireContext().applicationContext)}
	val permissionsModel by viewModels<PermissionsModel> {PermissionsModel.Factory(requireContext().applicationContext)}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val permissionsController by lazy { PermissionsController(requireActivity()) }
		val calendarPageController by lazy { CalendarPageController(calendarSettingsModel, permissionsModel, permissionsController) }

		val binding = CalendarPageBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.settingsModel = calendarSettingsModel
		binding.controller = calendarPageController
		return binding.root
	}

	override fun onResume() {
		super.onResume()
		// update the model, used in the controller to know to show the prompt
		permissionsModel.update()
	}
}