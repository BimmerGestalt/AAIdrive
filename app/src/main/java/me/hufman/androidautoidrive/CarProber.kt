package me.hufman.androidautoidrive

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CertMangling
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.SecurityService
import java.io.IOException
import java.net.Socket

/**
 * Tries to connect to a car
 */
class CarProber(val bmwCert: ByteArray, val miniCert: ByteArray): HandlerThread("CarProber") {
	companion object {
		val PORTS = listOf(4004, 4005, 4006, 4007, 4008)
		val TAG = "CarProber"
	}

	var handler: Handler? = null
	val ProberTask = Runnable {
		for (port in PORTS) {
			try {
				val socket = Socket("127.0.0.1", port)
				if (socket.isConnected) {
					// we found a car proxy! probably
					// let's try connecting to it
					Log.i(TAG, "Found open socket at $port, detecting car brand")
					probeCar(port)
				}
			} catch (e: IOException) {
				// this port isn't open
			}
		}

		schedule(2000)
	}

	override fun onLooperPrepared() {
		handler = Handler(looper)
		schedule(1000)
	}

	fun schedule(delay: Long) {
		handler?.removeCallbacks(ProberTask)
		if (!IDriveConnectionListener.isConnected) {
			handler?.postDelayed(ProberTask, delay)
		}
	}

	/**
	 * Attempts to detect the car brand
	 */
	private fun probeCar(port: Int) {
		if (!SecurityService.isConnected()) {
			// try again later after the security service is ready
			schedule(2000)
			return
		}
		// try logging in as if it were a bmw or a mini
		var success = false
		var errorMessage: String? = null
		for (brand in listOf("bmw", "mini")) {
			try {
				val cert = if (brand == "bmw") bmwCert else miniCert
				val signedCert = CertMangling.mergeBMWCert(cert, SecurityService.fetchBMWCerts(brandHint = brand))
				val conn = IDriveConnection.getEtchConnection("127.0.0.1", port, BaseBMWRemotingClient())
				val sas_challenge = conn.sas_certificate(signedCert)
				val sas_login = SecurityService.signChallenge(challenge = sas_challenge)
				conn.sas_login(sas_login)
				val capabilities = conn.rhmi_getCapabilities("", 255)
				val vehicleType = capabilities["vehicle.type"] as? String?
				val hmiType = capabilities["hmi.type"] as? String?
				Analytics.reportCarProbeDiscovered(port, vehicleType, hmiType)
				Log.i(TAG, "Probing detected a HMI type $hmiType")
				if (hmiType?.startsWith("BMW") == true) {
					// BMW brand
					setConnectedState(port, "bmw")
					success = true
					break
				}
				if (hmiType?.startsWith("MINI") == true) {
					// MINI connected
					setConnectedState(port, "mini")
					success = true
					break
				}
			} catch (e: BMWRemoting.SecurityException) {
				// Car rejected this cert
				errorMessage = e.message
			}
		}
		if (!success) {
			Analytics.reportCarProbeFailure(port, errorMessage)
		}
	}

	private fun setConnectedState(port: Int, brand: String) {
		Log.i(TAG, "Successfully detected $brand connection at port $port")
		IDriveConnectionListener.setConnection(brand, "127.0.0.1", port)
	}
}