package me.hufman.androidautoidrive.carapp.carinfo

import android.util.Log
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import me.hufman.androidautoidrive.AppSettingsViewer
import me.hufman.androidautoidrive.MainService
import me.hufman.androidautoidrive.carapp.CarAppService
import me.hufman.androidautoidrive.carapp.ReadoutCommandsReceiver

class CarInfoAppService: CarAppService()  {

	var carApp: CarInfoApp? = null
	var readoutController: ReadoutCommandsReceiver? = null

	override fun shouldStartApp(): Boolean = true

	override fun onCarStart() {
		Log.i(MainService.TAG, "Starting car info app")

		val handler = handler!!
		val carApp = CarInfoApp(iDriveConnectionStatus, securityAccess,
			CarAppAssetResources(applicationContext, "news"),
			CarAppAssetResources(applicationContext, "carinfo_unsigned"),
			handler, applicationContext.resources, AppSettingsViewer()
		)
		this.carApp = carApp
		readoutController = ReadoutCommandsReceiver(carApp.readoutCommands)
		readoutController?.register(this, handler)
	}

	override fun onCarStop() {
		carApp?.disconnect()
		carApp = null
		readoutController?.unregister(this)
	}
}