package me.hufman.androidautoidrive

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess

class SecurityServiceThread(val securityAccess: SecurityAccess): HandlerThread("SecurityServiceThread") {
	override fun onLooperPrepared() {
		securityAccess.connect()
	}

	fun connect() {
		Handler(Looper.myLooper()!!).post {
			securityAccess.connect()
		}
	}

	fun disconnect() {
		Handler(Looper.myLooper()!!).post {
			securityAccess.disconnect()
			quitSafely()
		}
	}
}