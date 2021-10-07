package me.hufman.androidautoidrive

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import me.hufman.androidautoidrive.carapp.notifications.ID5StatusbarApp
import me.hufman.androidautoidrive.carapp.notifications.NotificationSettings
import me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications
import me.hufman.androidautoidrive.carapp.notifications.ReadoutApp
import me.hufman.androidautoidrive.connections.BtStatus
import me.hufman.androidautoidrive.notifications.AudioPlayer
import me.hufman.androidautoidrive.notifications.CarNotificationControllerIntent
import me.hufman.androidautoidrive.notifications.NotificationListenerServiceImpl
import me.hufman.androidautoidrive.utils.GraphicsHelpersAndroid

class NotificationService(val context: Context, val iDriveConnectionStatus: IDriveConnectionStatus, val securityAccess: SecurityAccess, val carInformationObserver: CarInformationObserver) {
	var threadNotifications: CarThread? = null
	var carappNotifications: PhoneNotifications? = null
	var carappStatusbar: ID5StatusbarApp? = null
	var carappReadout: ReadoutApp? = null
	var running = false

	fun start(): Boolean {
		if (AppSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS].toBoolean()) {
			running = true
			synchronized(this) {
				if (carInformationObserver.capabilities.isNotEmpty() && threadNotifications?.isAlive != true) {
					threadNotifications = CarThread("Notifications") {
						Log.i(MainService.TAG, "Starting notifications app")
						val handler = threadNotifications?.handler
						if (handler == null) {
							Log.e(MainService.TAG, "CarThread Handler is null?")
						}
						val notificationSettings = NotificationSettings(carInformationObserver.capabilities, BtStatus(context) {}, MutableAppSettingsReceiver(context, handler))
						notificationSettings.notificationListenerConnected = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) == true
						notificationSettings.btStatus.register()
						carappNotifications = PhoneNotifications(iDriveConnectionStatus, securityAccess,
								CarAppAssetResources(context, "basecoreOnlineServices"),
								PhoneAppResourcesAndroid(context),
								GraphicsHelpersAndroid(),
								CarNotificationControllerIntent(context),
								AudioPlayer(context),
								notificationSettings)
						if (handler != null) {
							carappNotifications?.onCreate(context, handler)
						}
						// request an initial draw
						context.sendBroadcast(Intent(NotificationListenerServiceImpl.INTENT_REQUEST_DATA))

						handler?.post {
							if (running) {
								// start up the readout app
								// using a handler to automatically handle shutting down during init
								val carappReadout = ReadoutApp(iDriveConnectionStatus, securityAccess,
									CarAppAssetResources(context, "news"))
								carappNotifications?.readoutInteractions?.readoutController = carappReadout.readoutController
								this.carappReadout = carappReadout
							}
						}
						handler?.post {
							val id4 = carInformationObserver.capabilities["hmi.type"]?.contains("ID4")
							if (running && id4 == false) {
								// start up the id5 statusbar app
								// using a handler to automatically handle shutting down during init
								val carappStatusbar = ID5StatusbarApp(iDriveConnectionStatus, securityAccess,
									CarAppWidgetAssetResources(context, "bmwone"), GraphicsHelpersAndroid())
								// main app should use this for popup access
								carappNotifications?.viewPopup = carappStatusbar.popupView
								// main app should use this for statusbar access
								carappNotifications?.statusbarController?.controller = carappStatusbar.statusbarController
								// the statusbar can trigger the main app
								carappNotifications?.showNotificationController?.also {
									carappStatusbar.showNotificationController = it
								}
								this.carappStatusbar = carappStatusbar
								println("Finished initializing id5 statusbar")
							}
						}
					}
					threadNotifications?.start()
				}
			}
			return true
		} else {    // we should not run the service
			if (threadNotifications != null) {
				Log.i(MainService.TAG, "Notifications app needs to be shut down...")
				stop()
			}
			return false
		}
	}

	fun stop() {
		running = false
		// unregister in the main thread
		// when the car disconnects, the threadNotifications handler shuts down
		try {
			carappNotifications?.notificationSettings?.btStatus?.unregister()
			carappNotifications?.onDestroy(context)
		} catch (e: Exception) {
			Log.w(TAG, "Encountered an exception while shutting down", e)
		}

		// post cleanup actions to the thread to run after initialization finishes
		// if the car is already disconnected, this Handler loop will have crashed
		threadNotifications?.post {
			carappNotifications?.onDestroy(context)
			carappNotifications?.disconnect()
			carappNotifications = null
			carappReadout?.disconnect()
			carappReadout = null
			carappStatusbar?.disconnect()
			carappStatusbar = null
			threadNotifications?.quit()
			threadNotifications = null

			// if we started up again during shutdown
			if (running) {
				start()
			}
		}
	}
}