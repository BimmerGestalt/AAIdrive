package me.hufman.androidautoidrive

import android.content.Context
import me.hufman.androidautoidrive.carapp.maps.MapAppMode
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess

class MapService(val context: Context, val iDriveConnectionStatus: IDriveConnectionStatus, val securityAccess: SecurityAccess, val mapAppMode: MapAppMode) {
	fun start(): Boolean {
		return false
	}

	fun stop() {

	}
}