package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.databinding.MapSettingsBinding
import me.hufman.androidautoidrive.phoneui.viewmodels.MapSettingsModel

class MapSettingsFragment: Fragment() {
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val settingsModel = ViewModelProvider(this, MapSettingsModel.Factory(requireContext().applicationContext)).get(MapSettingsModel::class.java)
		val binding = MapSettingsBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.settings = settingsModel
		return binding.root
	}
}