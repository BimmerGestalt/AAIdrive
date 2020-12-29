package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_mapspage.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BooleanLiveSetting
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.controllers.PermissionsController
import me.hufman.androidautoidrive.phoneui.viewmodels.PermissionsModel
import me.hufman.androidautoidrive.phoneui.visible

class MapsPageFragment: Fragment() {
	val appSettings by lazy { MutableAppSettingsReceiver(requireContext()) }
	val permissionsController by lazy { PermissionsController(requireActivity()) }
	val viewModel by lazy { PermissionsModel.Factory(requireContext().applicationContext).create(PermissionsModel::class.java) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_mapspage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val mapsEnabledSetting = BooleanLiveSetting(requireContext().applicationContext, AppSettings.KEYS.ENABLED_GMAPS)
		mapsEnabledSetting.observe(viewLifecycleOwner) {
			swMapsEnabled.isChecked = it
			paneMaps.visible = it
		}
		swMapsEnabled.setOnCheckedChangeListener { _, isChecked ->
			onChangedSwitchGMaps(mapsEnabledSetting, isChecked)
		}

		viewModel.hasLocationPermission.observe(viewLifecycleOwner) {
			if (!it) {
				mapsEnabledSetting.setValue(false)
			}
		}
	}

	fun onChangedSwitchGMaps(setting: BooleanLiveSetting, isChecked: Boolean) {
		setting.setValue(isChecked)
		if (isChecked) {
			// make sure we have permissions to show current location
			if (viewModel.hasLocationPermission.value != true) {
				permissionsController.promptLocation()
			}
		}
	}

	override fun onResume() {
		super.onResume()
		viewModel.update()
	}
}