package me.hufman.androidautoidrive

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import io.bimmergestalt.idriveconnectkit.IDriveConnection
import io.bimmergestalt.idriveconnectkit.android.CertMangling
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Tries to connect to a car
 */
class CarProber(val securityAccess: SecurityAccess, val settings: AppSettings, val bmwCert: ByteArray?, val miniCert: ByteArray?, val j29Cert: ByteArray?): HandlerThread("CarProber") {
	companion object {
		val PORTS = listOf(4004, 4005, 4006, 4007, 4008)
		val TAG = "CarProber"
	}

	var handler: Handler? = null
	var carConnection: BMWRemotingServer? = null

	val ProberTask = Runnable {
		val configuredIps = settings[AppSettings.KEYS.CONNECTION_PROBE_IPS]
		if (configuredIps.isNotEmpty()) {
			for (configuredIp in configuredIps.split(',')) {
				val parts = configuredIp.split(':')
				val host = parts[0].trim()
				val port = parts.getOrNull(1)?.trim()?.toIntOrNull()
				if (port != null) {   // the user gave us a valid port
					if (probePort(host, port)) {
						// let's try connecting to it
						Log.i(TAG, "Found open socket at $host:$port, detecting car brand")
						probeCar(host, port)
					}
				} else {
					// scan the car ports on this host
					probeHost(host)
				}
			}
		}
		// scan the car ports from local BCL tunnel
		probeHost("127.0.0.1")
		schedule(2000)
		if (IDriveConnectionStatus.isConnected && carConnection == null) {
			// weird state, assert that we really have no connection
			IDriveConnectionStatus.reset()
		}
	}

	val KeepaliveTask = Runnable {
		if (!pingCar()) {
			// car has disconnected
			Log.i(TAG, "Previously-connected car has disconnected")
			IDriveConnectionStatus.reset()
			schedule(5000)
		}

		schedule(5000)
	}

	override fun onLooperPrepared() {
		handler = Handler(looper)
		schedule(1000)
	}

	private fun isConnected(): Boolean {
		return IDriveConnectionStatus.isConnected && carConnection != null
	}

	fun schedule(delay: Long) {
		if (!isConnected()) {
			handler?.removeCallbacks(ProberTask)
			handler?.postDelayed(ProberTask, delay)
		} else {
			handler?.removeCallbacks(KeepaliveTask)
			handler?.postDelayed(KeepaliveTask, delay)
		}
	}

	/** Tries connecting to a car at this host */
	private fun probeHost(host: String) {
		for (port in PORTS) {
			if (!isConnected() && probePort(host, port)) {
				// we found a car proxy! probably
				// let's try connecting to it
				Log.i(TAG, "Found open socket at $port, detecting car brand")
				probeCar(host, port)
			}
		}
	}

	/**
	 * Detects whether a port is open
	 */
	private fun probePort(host: String, port: Int): Boolean {
		try {
//			Log.d(TAG, "Probing $host:$port")
			val socket = Socket()
			socket.connect(InetSocketAddress(host, port), 100)
			if (socket.isConnected) {
				socket.close()
				return true
			}
		}
		 catch (e: IOException) {
			// this port isn't open
		}
		return false
	}

	/**
	 * Attempts to detect the car brand
	 */
	private fun probeCar(host: String, port: Int) {
		if (!securityAccess.isConnected()) {
			// try again later after the security service is ready
			schedule(2000)
			return
		}
		// try logging in as if it were a bmw or a mini
		var success = false
		var errorMessage: String? = null
		var errorException: Throwable? = null
		for (brand in listOf("bmw", "mini", "j29")) {
			try {
				val cert = when(brand) {
					"bmw" -> bmwCert
					"mini" -> miniCert
					else -> j29Cert
				} ?: continue       // j29Cert is optional cdsBaseApp from MyBMW
				val signedCert = CertMangling.mergeBMWCert(cert, securityAccess.fetchBMWCerts(brandHint = brand))
				val conn = IDriveConnection.getEtchConnection(host, port, BaseBMWRemotingClient())
				val sas_challenge = conn.sas_certificate(signedCert)
				val sas_login = securityAccess.signChallenge(challenge = sas_challenge)
				conn.sas_login(sas_login)
				val capabilities = conn.rhmi_getCapabilities("", 255)
				carConnection = conn

				val vehicleType = capabilities["vehicle.type"] as? String?
				val hmiType = capabilities["hmi.type"] as? String?
				Analytics.reportCarProbeDiscovered(port, vehicleType, hmiType)
				Log.i(TAG, "Probing detected a HMI type $hmiType")
				if (hmiType?.startsWith("BMW") == true) {
					// BMW brand
					setConnectedState(host, port, "bmw")
					success = true
					break
				}
				if (hmiType?.startsWith("MINI") == true) {
					// MINI connected
					setConnectedState(host, port, "mini")
					success = true
					break
				}
				if (brand == "j29") {
					setConnectedState(host, port, "j29")
					success = true
					break
				}
			} catch (e: Exception) {
				// Car rejected this cert
				errorMessage = e.message
				errorException = e
				Log.w(TAG, "Exception while probing car", e)
			}
		}
		if (!success) {
			Analytics.reportCarProbeFailure(port, errorMessage, errorException)
		}
	}

	private fun pingCar(): Boolean {
		try {
			return carConnection?.ver_getVersion() != null
		} catch (e: java.lang.Exception) {
			carConnection = null
			Log.w(TAG, "Exception while pinging car", e)
			return false
		}
	}

	private fun setConnectedState(host: String, port: Int, brand: String) {
		Log.i(TAG, "Successfully detected $brand connection at port $host:$port")
		IDriveConnectionStatus.setConnection(brand, host, port)
	}
}