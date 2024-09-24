package me.hufman.androidautoidrive.carapp.notifications

import android.content.Intent
import android.util.Log
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.CarAppService
import me.hufman.androidautoidrive.carapp.ReadoutCommandsSender
import me.hufman.androidautoidrive.carapp.ReadoutController
import me.hufman.androidautoidrive.connections.BtStatus
import me.hufman.androidautoidrive.notifications.AudioPlayer
import me.hufman.androidautoidrive.notifications.CarNotificationControllerIntent
import me.hufman.androidautoidrive.notifications.NotificationListenerServiceImpl
import me.hufman.androidautoidrive.utils.GraphicsHelpersAndroid

class NotificationAppService: CarAppService() {
	val appSettings = AppSettingsViewer()
	var carappNotifications: NotificationApp? = null
	var carappStatusbar: ID5StatusbarApp? = null

	override fun shouldStartApp(): Boolean {
		return appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS].toBoolean()
	}

	override fun onCarStart() {
		Log.i(MainService.TAG, "Starting notifications app")
		val handler = handler!!
		val notificationSettings = NotificationSettings(carInformation.capabilities, BtStatus(applicationContext) {}, MutableAppSettingsReceiver(applicationContext, handler))
		notificationSettings.btStatus.register()
		carappNotifications = NotificationApp(iDriveConnectionStatus, securityAccess,
				CarAppAssetResources(applicationContext, "basecoreOnlineServices"),
				PhoneAppResourcesAndroid(applicationContext),
				GraphicsHelpersAndroid(),
				CarNotificationControllerIntent(applicationContext),
				AudioPlayer(applicationContext),
				notificationSettings)
		carappNotifications?.onCreate(applicationContext, handler)
		carappNotifications?.readoutInteractions?.readoutController = ReadoutController("NotificationReadout", ReadoutCommandsSender(this))

		// request an initial draw
		// API24 can turn off the service, so we ask it to start up the service
		// The service automatically loads all the data onStart if the car is connected
		// but we also send a manual request if the service is already runnin
		NotificationListenerServiceImpl.startService(applicationContext)
		val intent = Intent(NotificationListenerServiceImpl.INTENT_REQUEST_DATA)
			.setPackage(packageName)
		applicationContext.sendBroadcast(intent)

		handler.post {
			val id4 = carInformation.capabilities["hmi.type"]?.contains("ID4")
			if (running && id4 == false) {
				// start up the id5 statusbar app
				// using a handler to automatically handle shutting down during init
				val carappStatusbar = ID5StatusbarApp(iDriveConnectionStatus, securityAccess,
						CarAppWidgetAssetResources(applicationContext, "bmwone"),
						CarAppWidgetAssetResources(applicationContext, "id5statusbar_unsigned"),
						GraphicsHelpersAndroid())
				carappNotifications?.id5Upgrade(carappStatusbar)
				this.carappStatusbar = carappStatusbar
				Log.i(MainService.TAG, "Finished initializing id5 statusbar")
			}
		}
	}

	override fun onCarStop() {
		// unregister in the main thread
		try {
			carappNotifications?.notificationSettings?.btStatus?.unregister()
			carappNotifications?.onDestroy(applicationContext)
		} catch (e: Exception) {
			Log.w(MainService.TAG, "Encountered an exception while shutting down Notifications", e)
		}

		NotificationListenerServiceImpl.shutdownService(this)
		carappNotifications?.disconnect()
		carappNotifications = null
		carappStatusbar?.disconnect()
		carappStatusbar = null
	}
}