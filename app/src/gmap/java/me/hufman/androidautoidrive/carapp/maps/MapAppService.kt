package me.hufman.androidautoidrive.carapp.maps

import android.hardware.display.VirtualDisplay
import android.util.Log
import io.bimmergestalt.idriveconnectkit.RHMIDimensions
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.CDSDataProvider
import me.hufman.androidautoidrive.carapp.CarAppService
import java.lang.Exception

class MapAppService: CarAppService() {
	val appSettings = AppSettingsViewer()
	var mapApp: MapApp? = null
	var mapScreenCapture: VirtualDisplayScreenCapture? = null
	var virtualDisplay: VirtualDisplay? = null
	var mapController: GMapsController? = null
	var mapListener: MapsInteractionControllerListener? = null

	override fun onCarStart() {
		if (appSettings[AppSettings.KEYS.ENABLED_GMAPS].toBoolean()) {
			Log.i(MainService.TAG, "Starting GMaps")
			val cdsData = CDSDataProvider()
			cdsData.setConnection(CarInformation.cdsData.asConnection(cdsData))
			val carLocationProvider = CarLocationProvider(cdsData)
			val mapAppMode = MapAppMode(RHMIDimensions.create(carInformation.capabilities), AppSettingsViewer())
			val mapScreenCapture = VirtualDisplayScreenCapture.build(mapAppMode.fullDimensions.visibleWidth, mapAppMode.fullDimensions.visibleHeight)
			this.mapScreenCapture = mapScreenCapture
			val virtualDisplay = VirtualDisplayScreenCapture.createVirtualDisplay(applicationContext, mapScreenCapture.imageCapture, 250)
			this.virtualDisplay = virtualDisplay
			val mapController = GMapsController(applicationContext, carLocationProvider, MapResultsSender(applicationContext), virtualDisplay, MutableAppSettingsReceiver(applicationContext, null /* specifically main thread */))
			this.mapController = mapController
			val mapListener = MapsInteractionControllerListener(applicationContext, mapController)
			mapListener.onCreate()
			this.mapListener = mapListener

			val mapApp = MapApp(iDriveConnectionStatus, securityAccess,
					CarAppAssetResources(applicationContext, "smartthings"),
					mapAppMode,
					MapInteractionControllerIntent(applicationContext), mapScreenCapture)
			this.mapApp = mapApp
			val handler = this.handler!!
			mapApp.onCreate(applicationContext, handler)
		}
	}

	override fun onCarStop() {
		// shut down maps functionality right away
		// when the car disconnects, the threadGMaps handler shuts down
		try {
			mapScreenCapture?.onDestroy()
			virtualDisplay?.release()
			// nothing to stop in mapController
			mapListener?.onDestroy()
			mapApp?.onDestroy(applicationContext)

			mapScreenCapture = null
			virtualDisplay = null
			mapController = null
			mapListener = null
		} catch (e: Exception) {
			Log.w(TAG, "Encountered an exception while shutting down Maps", e)
		}

		mapApp?.onDestroy(applicationContext)
		mapApp?.disconnect()
		mapApp = null
	}
}