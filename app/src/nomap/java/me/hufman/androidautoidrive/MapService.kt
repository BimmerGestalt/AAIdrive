package me.hufman.androidautoidrive

import android.content.Context
import me.hufman.androidautoidrive.carapp.maps.MapAppMode
import me.hufman.idriveconnectionkit.android.IDriveConnectionStatus
import me.hufman.idriveconnectionkit.android.security.SecurityAccess

class MapService(val context: Context, val iDriveConnectionStatus: IDriveConnectionStatus, val securityAccess: SecurityAccess, val mapAppMode: MapAppMode) {
	fun start(): Boolean {
		return false
	}

	fun stop() {

	}
}