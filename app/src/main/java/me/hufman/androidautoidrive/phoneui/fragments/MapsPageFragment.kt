package me.hufman.androidautoidrive.phoneui.fragments

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_mapspage.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.visible

class MapsPageFragment: Fragment() {
	val appSettings by lazy { MutableAppSettingsReceiver(requireContext()) }

	companion object {
		const val REQUEST_LOCATION = 4000
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_mapspage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		swMapsEnabled.setOnCheckedChangeListener { _, isChecked ->
			onChangedSwitchGMaps(isChecked)
			redraw()
		}
	}

	fun onChangedSwitchGMaps(isChecked: Boolean) {
		appSettings[AppSettings.KEYS.ENABLED_GMAPS] = isChecked.toString()
		if (isChecked) {
			// make sure we have permissions to show current location
			if (!hasLocationPermission()) {
				promptForLocation()
			}
		}
	}

	fun hasLocationPermission(): Boolean {
		return ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
	}
	fun promptForLocation() {
		ActivityCompat.requestPermissions(requireActivity(),
				arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
				REQUEST_LOCATION)
	}

	override fun onResume() {
		super.onResume()

		redraw()
	}

	fun redraw() {
		// reset the GMaps setting if we don't have permission
		if (!hasLocationPermission()) {
			appSettings[AppSettings.KEYS.ENABLED_GMAPS] = "false"
		}

		swMapsEnabled.isChecked = appSettings[AppSettings.KEYS.ENABLED_GMAPS].toBoolean()
		paneMaps.visible = appSettings[AppSettings.KEYS.ENABLED_GMAPS].toBoolean()
	}
}