package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.databinding.MapPageSettingsBinding
import me.hufman.androidautoidrive.phoneui.controllers.MapsPageController
import me.hufman.androidautoidrive.phoneui.controllers.PermissionsController
import me.hufman.androidautoidrive.phoneui.viewmodels.MapSettingsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.PermissionsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.viewModels

class MapsPageFragment: Fragment() {
	val mapSettingsModel by viewModels<MapSettingsModel> { MapSettingsModel.Factory(requireContext().applicationContext) }
	val permissionsModel by viewModels<PermissionsModel> { PermissionsModel.Factory(requireContext().applicationContext) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val permissionsController by lazy { PermissionsController(requireActivity()) }
		val mapsPageController by lazy { MapsPageController(mapSettingsModel, permissionsModel, permissionsController) }

		val binding = MapPageSettingsBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.settings = mapSettingsModel
		binding.controller = mapsPageController
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// clear the checkbox if we don't yet have location permission
		permissionsModel.hasLocationPermission.observe(viewLifecycleOwner) {
			if (!it) {
				mapSettingsModel.mapEnabled.setValue(false)
			}
		}
	}

	override fun onResume() {
		super.onResume()
		// update the model, used in the controller to know to show the prompt
		permissionsModel.update()
	}
}