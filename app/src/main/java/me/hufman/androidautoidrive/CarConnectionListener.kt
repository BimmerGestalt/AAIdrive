package me.hufman.androidautoidrive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener

/**
 * Starts the MainService when the car announces a connection
 *
 * On Android Oreo, this won't do anything, because we instead run MainService directly
 * to subscribe IDriveConnectionListener manually
 */
class CarConnectionListener: BroadcastReceiver() {
	private val TAG = "CarConnectionListener"

	override fun onReceive(context: Context?, intent: Intent?) {
		if (context == null || intent == null) return
		// car changed connection status
		Log.i(TAG, "Received car status announcement: ${intent.action}")

		if (intent.action == IDriveConnectionListener.INTENT_ATTACHED ||
				intent.action == "me.hufman.androidautoidrive.CarConnectionListener_START") {
			context.startService(Intent(context, MainService::class.java).setAction(MainService.ACTION_START))
		}
		if (intent.action == IDriveConnectionListener.INTENT_DETACHED ||
				intent.action == "me.hufman.androidautoidrive.CarConnectionListener_STOP") {
			context.stopService(Intent(context, MainService::class.java).setAction(MainService.ACTION_STOP))
		}

		context.sendBroadcast(Intent(MainActivity.INTENT_REDRAW))
	}
}