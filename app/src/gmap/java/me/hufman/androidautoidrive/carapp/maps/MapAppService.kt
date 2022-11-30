package me.hufman.androidautoidrive.carapp.maps

import android.hardware.display.VirtualDisplay
import android.util.Log
import io.bimmergestalt.idriveconnectkit.RHMIDimensions
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.CDSDataProvider
import me.hufman.androidautoidrive.carapp.CarAppService
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.maps.AndroidLocationProvider
import me.hufman.androidautoidrive.maps.CdsLocationProvider
import me.hufman.androidautoidrive.maps.CombinedLocationProvider
import me.hufman.androidautoidrive.maps.GMapsPlaceSearch
import java.lang.Exception

class MapAppService: CarAppService() {
	val appSettings = AppSettingsViewer()
	var mapAppMode: MapAppMode? = null
	var mapApp: MapApp? = null
	var mapScreenCapture: VirtualDisplayScreenCapture? = null
	var virtualDisplay: VirtualDisplay? = null
	var mapController: GMapsController? = null
	var mapListener: MapsInteractionControllerListener? = null

	override fun shouldStartApp(): Boolean {
		return appSettings[AppSettings.KEYS.ENABLED_MAPS].toBoolean()
	}

	override fun onCarStart() {
		Log.i(MainService.TAG, "Starting GMaps")
		val cdsData = CDSDataProvider()
		cdsData.setConnection(CarInformation.cdsData.asConnection(cdsData))
		val carLocationProvider = CombinedLocationProvider(
				appSettings, AndroidLocationProvider.getInstance(this), CdsLocationProvider(cdsData)
		)
		val mapAppMode = MapAppMode.build(RHMIDimensions.create(carInformation.capabilities), MutableAppSettingsReceiver(this, handler), cdsData, MusicAppMode.TRANSPORT_PORTS.fromPort(iDriveConnectionStatus.port) ?: MusicAppMode.TRANSPORT_PORTS.BT)
		this.mapAppMode = mapAppMode
		val mapScreenCapture = VirtualDisplayScreenCapture.build(mapAppMode)
		this.mapScreenCapture = mapScreenCapture
		val virtualDisplay = VirtualDisplayScreenCapture.createVirtualDisplay(applicationContext, mapScreenCapture.imageCapture, 250)
		this.virtualDisplay = virtualDisplay
		val mapController = GMapsController(applicationContext, carLocationProvider, virtualDisplay, MutableAppSettingsReceiver(applicationContext, null /* specifically main thread */), mapAppMode)
		this.mapController = mapController
		val mapPlaceSearch = GMapsPlaceSearch.getInstance(this, carLocationProvider)
		val mapListener = MapsInteractionControllerListener(applicationContext, mapController)
		mapListener.onCreate()
		this.mapListener = mapListener

		val mapApp = MapApp(iDriveConnectionStatus, securityAccess,
				CarAppAssetResources(applicationContext, "smartthings"),
				mapAppMode, carLocationProvider,
				MapInteractionControllerIntent(applicationContext), mapPlaceSearch, mapScreenCapture)
		this.mapApp = mapApp
		val handler = this.handler!!
		mapApp.onCreate(handler)
	}

	override fun onCarStop() {
		mapAppMode?.currentNavDestination = null

		// shut down maps functionality right away
		// when the car disconnects, the threadGMaps handler shuts down
		try {
			mapScreenCapture?.onDestroy()
			virtualDisplay?.release()
			// nothing to stop in mapController
			mapListener?.onDestroy()
			mapApp?.onDestroy()

			mapScreenCapture = null
			virtualDisplay = null
			mapController = null
			mapListener = null
		} catch (e: Exception) {
			Log.w(TAG, "Encountered an exception while shutting down Maps", e)
		}

		mapApp?.onDestroy()
		mapApp?.disconnect()
		mapApp = null
	}
}