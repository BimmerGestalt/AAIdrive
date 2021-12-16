package me.hufman.androidautoidrive.carapp.calendar

import android.util.Log
import io.bimmergestalt.idriveconnectkit.CDS
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MainService
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.calendar.CalendarProvider
import me.hufman.androidautoidrive.carapp.CarAppService
import me.hufman.androidautoidrive.carapp.maps.LatLong
import me.hufman.androidautoidrive.carapp.navigation.AndroidGeocoderSearcher
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsDouble
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonObject
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonPrimitive

class CalendarAppService: CarAppService() {

	val appSettings by lazy { MutableAppSettingsReceiver(applicationContext) }
	var carappCalendar: CalendarApp? = null

	override fun shouldStartApp(): Boolean {
		return appSettings[AppSettings.KEYS.ENABLED_CALENDAR].toBoolean()
	}

	override fun onCarStart() {
		Log.i(MainService.TAG, "Starting calendar app")
		carappCalendar = CalendarApp(iDriveConnectionStatus, securityAccess,
				CarAppAssetResources(applicationContext, "calendar"),
				CalendarProvider(applicationContext, appSettings),
				AndroidGeocoderSearcher(applicationContext))
		carappCalendar?.onCreate()
		handler?.post {
			if (appSettings[AppSettings.KEYS.CALENDAR_AUTOMATIC_NAVIGATION].toBoolean()) {
				val carPosition = carInformation.cdsData[CDS.NAVIGATION.GPSPOSITION]
				val location = LatLong(carPosition?.tryAsJsonObject("GPSPosition")?.tryAsJsonPrimitive("latitude")?.tryAsDouble ?: 0.0,
						carPosition?.tryAsJsonObject("GPSPosition")?.tryAsJsonPrimitive("longitude")?.tryAsDouble ?: 0.0)
				carappCalendar?.navigateNextDestination(location)
			}
		}
	}

	override fun onCarStop() {
		carappCalendar?.disconnect()
		carappCalendar = null
	}
}