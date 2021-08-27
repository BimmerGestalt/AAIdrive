package me.hufman.androidautoidrive.phoneui.controllers

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import me.hufman.androidautoidrive.R

class OverviewPageController(val fragment: Fragment) {
	fun onClickConnecting() {
		fragment.findNavController().navigate(R.id.nav_connection)
	}
}