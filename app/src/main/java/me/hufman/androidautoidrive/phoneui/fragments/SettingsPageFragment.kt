package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_settingspage.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.ViewHelpers.visible
import me.hufman.androidautoidrive.phoneui.viewmodels.LanguageSettingsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.viewModels

class SettingsPageFragment: Fragment() {
	val viewModel by viewModels<LanguageSettingsModel> { LanguageSettingsModel.Factory(requireContext().applicationContext) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_settingspage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		swAdvancedSettings.setOnClickListener {
			viewModel.showAdvanced.setValue(swAdvancedSettings.isChecked)
		}
		viewModel.showAdvanced.observe(viewLifecycleOwner) {
			swAdvancedSettings.isChecked = it
			paneAdvancedSettings.visible = it
		}
	}
}
