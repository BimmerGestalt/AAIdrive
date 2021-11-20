package me.hufman.androidautoidrive.carapp.notifications

import android.content.Intent
import android.provider.Settings
import android.util.Log
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.CarAppService
import me.hufman.androidautoidrive.connections.BtStatus
import me.hufman.androidautoidrive.notifications.AudioPlayer
import me.hufman.androidautoidrive.notifications.CarNotificationControllerIntent
import me.hufman.androidautoidrive.notifications.NotificationListenerServiceImpl
import me.hufman.androidautoidrive.utils.GraphicsHelpersAndroid

class NotificationAppService: CarAppService() {
	val appSettings = AppSettingsViewer()
	var carappNotifications: PhoneNotifications? = null
	var carappReadout: ReadoutApp? = null
	var carappStatusbar: ID5StatusbarApp? = null

	override fun onCarStart() {
		if (appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS].toBoolean()) {

			Log.i(MainService.TAG, "Starting notifications app")
			val handler = handler!!
			val notificationSettings = NotificationSettings(carInformation.capabilities, BtStatus(applicationContext) {}, MutableAppSettingsReceiver(applicationContext, handler))
			notificationSettings.notificationListenerConnected = Settings.Secure.getString(applicationContext.contentResolver, "enabled_notification_listeners")?.contains(applicationContext.packageName) == true
			notificationSettings.btStatus.register()
			carappNotifications = PhoneNotifications(iDriveConnectionStatus, securityAccess,
					CarAppAssetResources(applicationContext, "basecoreOnlineServices"),
					PhoneAppResourcesAndroid(applicationContext),
					GraphicsHelpersAndroid(),
					CarNotificationControllerIntent(applicationContext),
					AudioPlayer(applicationContext),
					notificationSettings)
			carappNotifications?.onCreate(applicationContext, handler)
			// request an initial draw
			applicationContext.sendBroadcast(Intent(NotificationListenerServiceImpl.INTENT_REQUEST_DATA))

			handler.post {
				if (running) {
					// start up the readout app
					// using a handler to automatically handle shutting down during init
					val carappReadout = ReadoutApp(iDriveConnectionStatus, securityAccess,
							CarAppAssetResources(applicationContext, "news"))
					carappNotifications?.readoutInteractions?.readoutController = carappReadout.readoutController
					this.carappReadout = carappReadout
				}
			}
			handler.post {
				val id4 = carInformation.capabilities["hmi.type"]?.contains("ID4")
				if (running && id4 == false) {
					// start up the id5 statusbar app
					// using a handler to automatically handle shutting down during init
					val carappStatusbar = ID5StatusbarApp(iDriveConnectionStatus, securityAccess,
							CarAppWidgetAssetResources(applicationContext, "bmwone"), GraphicsHelpersAndroid())
					// main app should use this for popup access
					carappNotifications?.viewPopup = carappStatusbar.popupView
					// main app should use this for statusbar access
					carappNotifications?.statusbarController?.controller = carappStatusbar.statusbarController
					// the statusbar can trigger the main app
					carappNotifications?.showNotificationController?.also {
						carappStatusbar.showNotificationController = it
					}
					this.carappStatusbar = carappStatusbar
					Log.i(MainService.TAG, "Finished initializing id5 statusbar")
				}
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

		carappNotifications?.disconnect()
		carappNotifications = null
		carappReadout?.disconnect()
		carappReadout = null
		carappStatusbar?.disconnect()
		carappStatusbar = null
	}
}