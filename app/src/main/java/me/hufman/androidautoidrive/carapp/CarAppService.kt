package me.hufman.androidautoidrive.carapp

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.util.Log
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionReceiver
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.CarThread
import java.lang.RuntimeException

abstract class CarAppService: Service() {
	private val TAG by lazy(this.javaClass::getSimpleName)

	// if we are told to start up again during the shutdown process
	var running = false
		private set

	// where the car app runs, on its own thread
	var thread: CarThread? = null
	val handler: Handler?
		get() = thread?.handler

	// will need changes if the service gets split to separate processes
	val carInformation = CarInformation()
	val iDriveConnectionStatus: IDriveConnectionStatus = IDriveConnectionReceiver()
	val securityAccess by lazy { SecurityAccess.getInstance(applicationContext) }

	override fun onCreate() {
		super.onCreate()
		securityAccess.connect()
	}

	/**
	 * When a car is connected, it will bind the Car App Service
	 */
	override fun onBind(intent: Intent?): IBinder? {
		intent ?: return null
		setConnection(intent)
		startThread()
		return null
	}

	/**
	 * If the thread crashes for any reason,
	 * opening the main app will trigger a Start on this Car App Service
	 * as a chance to reconnect
	 */
	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		super.onStartCommand(intent, flags, startId)
		intent ?: return START_NOT_STICKY
		setConnection(intent)
		startThread()
		return START_STICKY
	}

	/**
	 * The car has disconnected, so forget the previous details and shut down
	 *
	 * Until we are split out to a separate process, rely on the MainService to handle this
	 */
	override fun onUnbind(intent: Intent?): Boolean {
//		IDriveConnectionStatus.reset()
		stopThread()
		return super.onUnbind(intent)
	}

	override fun onDestroy() {
		stopThread()
		super.onDestroy()
	}

	/**
	 * Parses the connection intent and sets the connection details
	 *
	 * Until we are split out to a separate process, rely on the MainService to handle this
	 */
	fun setConnection(intent: Intent) {
		IDriveConnectionReceiver().onReceive(applicationContext, intent)
	}

	/**
	 * Starts the thread for the car app, if it isn't running
	 */
	fun startThread() {
		running = true

		if (iDriveConnectionStatus.isConnected &&
				securityAccess.isConnected() &&
				thread?.isAlive != true) {

			thread = CarThread(TAG) {
				onCarStart()
			}
			thread?.start()
		}
	}

	/**
	 * Stops the thread for the car app, if it is still running
	 *
	 * If the car forcefully disconnects, the thread will have crashed already
	 * But if the service is being toggled through the UI, we must still gracefully stop
	 */
	fun stopThread() {
		running = false
		onCarStop()

		// shut down the thread after finishing any startup
		try {
			thread?.post {
				// try to stop again after any startup runnables have run
				onCarStop()
				thread?.quit()
				thread = null

				// if we were told to start up again during shutting down, start again
				if (running) {
					startThread()
				}
			}
		} catch (e: RuntimeException) {
			// thread is already dead
		}
	}

	abstract fun onCarStart()

	abstract fun onCarStop()
}