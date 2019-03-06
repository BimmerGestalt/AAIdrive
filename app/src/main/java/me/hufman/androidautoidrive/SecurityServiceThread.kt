package me.hufman.androidautoidrive

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import me.hufman.idriveconnectionkit.android.SecurityService

class SecurityServiceThread(val context: Context): HandlerThread("SecurityServiceThread") {
	override fun onLooperPrepared() {
		SecurityService.connect(context)
	}

	fun connect() {
		Handler(Looper.myLooper()).post {
			SecurityService.connect(context)
		}
	}

	fun disconnect() {
		Handler(Looper.myLooper()).post {
			SecurityService.disconnect()
			quitSafely()
		}
	}
}