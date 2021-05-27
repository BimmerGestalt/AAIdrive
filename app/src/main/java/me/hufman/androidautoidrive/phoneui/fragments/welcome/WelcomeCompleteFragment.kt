package me.hufman.androidautoidrive.phoneui.fragments.welcome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.R

class WelcomeCompleteFragment: Fragment() {
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_welcome_complete, container, false)
	}

	override fun onResume() {
		super.onResume()

		AppSettings.saveSetting(requireContext(), AppSettings.KEYS.FIRST_START_DONE, "true")
	}
}