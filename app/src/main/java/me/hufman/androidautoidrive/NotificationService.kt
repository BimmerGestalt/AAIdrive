package me.hufman.androidautoidrive

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import me.hufman.androidautoidrive.carapp.notifications.NotificationSettings
import me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications
import me.hufman.androidautoidrive.carapp.notifications.ReadoutApp
import me.hufman.androidautoidrive.connections.BtStatus
import me.hufman.androidautoidrive.notifications.AudioPlayer
import me.hufman.androidautoidrive.notifications.CarNotificationControllerIntent
import me.hufman.androidautoidrive.notifications.NotificationListenerServiceImpl
import me.hufman.androidautoidrive.utils.GraphicsHelpersAndroid
import me.hufman.idriveconnectionkit.android.IDriveConnectionStatus
import me.hufman.idriveconnectionkit.android.security.SecurityAccess

class NotificationService(val context: Context, val iDriveConnectionStatus: IDriveConnectionStatus, val securityAccess: SecurityAccess, val carInformationObserver: CarInformationObserver) {
	var threadNotifications: CarThread? = null
	var carappNotifications: PhoneNotifications? = null
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
								CarAppAssetManager(context, "basecoreOnlineServices"),
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
										CarAppAssetManager(context, "news"))
								carappNotifications?.readoutInteractions?.readoutController = carappReadout.readoutController
								this.carappReadout = carappReadout
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
		// post it to the thread to run after initialization finishes
		threadNotifications?.post {
			if (!running) { // check that we do actually intend to shut down
				carappNotifications?.notificationSettings?.btStatus?.unregister()
				carappNotifications?.onDestroy(context)
				carappNotifications = null
				carappReadout?.onDestroy()
				carappReadout = null
				threadNotifications?.quit()
				threadNotifications = null

				// if we started up again during shutdown
				if (running) {
					start()
				}
			}
		}
	}
}