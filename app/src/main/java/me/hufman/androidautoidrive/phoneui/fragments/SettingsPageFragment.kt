package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_settingspage.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.visible

class SettingsPageFragment: Fragment() {
	val appSettings by lazy { MutableAppSettingsReceiver(requireContext()) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_settingspage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		swAdvancedSettings.setOnClickListener {
			appSettings[AppSettings.KEYS.SHOW_ADVANCED_SETTINGS] = swAdvancedSettings.isChecked.toString()
			redraw()
		}
	}

	override fun onResume() {
		super.onResume()

		redraw()
	}

	fun redraw() {
		swAdvancedSettings.isChecked = appSettings[AppSettings.KEYS.SHOW_ADVANCED_SETTINGS].toBoolean()
		paneAdvancedSettings.visible = appSettings[AppSettings.KEYS.SHOW_ADVANCED_SETTINGS].toBoolean()
	}
}
