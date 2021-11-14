package me.hufman.androidautoidrive.carapp.carinfo

import android.util.Log
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import me.hufman.androidautoidrive.CarInformationDiscovery
import me.hufman.androidautoidrive.CarInformationUpdater
import me.hufman.androidautoidrive.MainService
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.carapp.CDSConnection
import me.hufman.androidautoidrive.carapp.CDSConnectionAsync
import me.hufman.androidautoidrive.carapp.CarAppService

class CarInformationDiscoveryService: CarAppService() {
	val appSettings by lazy { MutableAppSettingsReceiver(applicationContext) }
	var carappCapabilities: CarInformationDiscovery? = null

	override fun onCarStart() {
		Log.i(MainService.TAG, "Starting to discover car capabilities")
		val handler = handler!!

		// receiver to receive capabilities and cds properties
		// wraps the CDSConnection with a Handler async wrapper
		val carInformationUpdater = object: CarInformationUpdater(appSettings) {
			override fun onCdsConnection(connection: CDSConnection?) {
				super.onCdsConnection(connection?.let { CDSConnectionAsync(handler, connection) })
			}
		}

		carappCapabilities = CarInformationDiscovery(iDriveConnectionStatus, securityAccess,
				CarAppAssetResources(applicationContext, "smartthings"), carInformationUpdater)
		carappCapabilities?.onCreate()
	}

	override fun onCarStop() {
		carappCapabilities?.onDestroy()
		carappCapabilities = null
	}
}