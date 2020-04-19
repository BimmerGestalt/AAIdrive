package me.hufman.androidautoidrive.carapp.carprojection

import me.hufman.carprojection.CarProjectionHost
import me.hufman.carprojection.ProjectionAppInfo

object ProjectionState {
	var selectedApp: ProjectionAppInfo? = null
	var carProjectionHost: CarProjectionHost? = null
	var openInput: (() -> Unit)? = null
}