package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_connectionpage.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.AppSettingsViewer
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.visible

class ConnectionPageFragment: Fragment() {
	val appSettings by lazy { AppSettingsViewer() }
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_connectionpage, container, false)
	}

	override fun onResume() {
		super.onResume()
		paneCarAdvancedInfo.visible = appSettings[AppSettings.KEYS.SHOW_ADVANCED_SETTINGS].toBoolean()
	}
}