package me.hufman.androidautoidrive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionReceiver

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

		try {
			if (intent.action == IDriveConnectionReceiver.INTENT_ATTACHED ||
					intent.action == "me.hufman.androidautoidrive.CarConnectionListener_START") {
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
					// this is a clear signal of car connection, we can confidently startForeground
					context.startForegroundService(Intent(context, MainService::class.java).setAction(MainService.ACTION_START))
				} else {
					context.startService(Intent(context, MainService::class.java).setAction(MainService.ACTION_START))
				}
			}
			if (intent.action == IDriveConnectionReceiver.INTENT_DETACHED ||
					intent.action == "me.hufman.androidautoidrive.CarConnectionListener_STOP") {
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
					// we have to startForegroundService everywhere
					context.startForegroundService(Intent(context, MainService::class.java).setAction(MainService.ACTION_STOP))
				} else {
					context.startService(Intent(context, MainService::class.java).setAction(MainService.ACTION_STOP))
				}
			}
		} catch (e: Exception) {
			Log.w(TAG, "Failed to start MainService", e)
		}
	}

	fun register(context: Context) {
		ContextCompat.registerReceiver(context, this, IntentFilter(IDriveConnectionReceiver.INTENT_ATTACHED), RECEIVER_NOT_EXPORTED)
		ContextCompat.registerReceiver(context, this, IntentFilter(IDriveConnectionReceiver.INTENT_DETACHED), RECEIVER_NOT_EXPORTED)
	}
	fun unregister(context: Context) {
		context.unregisterReceiver(this)
	}
}