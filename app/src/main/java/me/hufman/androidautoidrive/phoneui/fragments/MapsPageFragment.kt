package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_mapspage.*
import me.hufman.androidautoidrive.databinding.MapPageSettingsBinding
import me.hufman.androidautoidrive.phoneui.controllers.PermissionsController
import me.hufman.androidautoidrive.phoneui.viewmodels.MapSettingsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.PermissionsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.viewModels

class MapsPageFragment: Fragment() {
	val mapSettingsModel by viewModels<MapSettingsModel> { MapSettingsModel.Factory(requireContext().applicationContext) }
	val permissionsController by lazy { PermissionsController(requireActivity()) }
	val permissionsModel by viewModels<PermissionsModel> { PermissionsModel.Factory(requireContext().applicationContext) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val binding = MapPageSettingsBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.settings = mapSettingsModel
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		swMapsEnabled.setOnCheckedChangeListener { _, isChecked ->
			onChangedSwitchGMaps(isChecked)
		}

		permissionsModel.hasLocationPermission.observe(viewLifecycleOwner) {
			if (!it) {
				mapSettingsModel.mapEnabled.setValue(false)
			}
		}
	}

	fun onChangedSwitchGMaps(isChecked: Boolean) {
		mapSettingsModel.mapEnabled.setValue(isChecked)
		if (isChecked) {
			// make sure we have permissions to show current location
			if (permissionsModel.hasLocationPermission.value != true) {
				permissionsController.promptLocation()
			}
		}
	}

	override fun onResume() {
		super.onResume()
		permissionsModel.update()
	}
}