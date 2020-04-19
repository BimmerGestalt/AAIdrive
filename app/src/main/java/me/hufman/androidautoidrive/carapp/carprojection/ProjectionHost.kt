package me.hufman.androidautoidrive.carapp.carprojection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.google.android.gms.maps.Projection
import me.hufman.androidautoidrive.carapp.maps.VirtualDisplayScreenCapture
import me.hufman.carprojection.AppDiscovery
import me.hufman.carprojection.CarProjectionHost
import me.hufman.carprojection.ProjectionAppInfo
import me.hufman.carprojection.adapters.ICarProjectionCallbackService
import me.hufman.carprojection.adapters.ICarService
import me.hufman.idriveconnectionkit.rhmi.RHMIState
import java.lang.Exception

class ProjectionHost(val context: Context, val virtualDisplay: VirtualDisplayScreenCapture, var inputState: RHMIState) {
	companion object {
		val TAG = "ProjectionHost"
	}

	// car parts
	var iCar: IBinder? = null
	var iCarProjectionCallback: IBinder? = null

	val carConnection = object: ServiceConnection {
		override fun onServiceDisconnected(p0: ComponentName?) {
			Log.i(TAG, "Disconnected from fake car")
			iCar = null
		}

		override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
			Log.i(TAG, "Connected to fake car $p1")
			iCar = p1
		}
	}

	val callbackConnection = object: ServiceConnection {
		override fun onServiceDisconnected(p0: ComponentName?) {
			Log.i(TAG, "Disconnected from fake car callback")
			iCarProjectionCallback = null
		}

		override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
			Log.i(TAG, "Connected to fake car callback $p1")
			iCarProjectionCallback = p1
		}
	}

	init {
		startCarService()
		startCarCallbackService()
	}

	private fun startCarService() {
		if (iCar != null) {
			return
		}
		val intent = Intent(context, ICarService::class.java)
		context.bindService(intent, carConnection, Context.BIND_AUTO_CREATE)
	}

	private fun startCarCallbackService() {
		if (iCarProjectionCallback != null) {
			return
		}
		val intent = Intent(context, ICarProjectionCallbackService::class.java)
		context.bindService(intent, callbackConnection, Context.BIND_AUTO_CREATE)
	}

	fun connect(app: ProjectionAppInfo) {
		val iCar = iCar ?: return
		val iCarProjectionCallback = iCarProjectionCallback ?: return
		if (ProjectionState.carProjectionHost?.appInfo != app) {
			disconnect()
		}

		ProjectionState.selectedApp = app
		val host = CarProjectionHost(context, app, virtualDisplay.imageCapture, iCar, iCarProjectionCallback)
		ProjectionState.carProjectionHost = host
		AppDiscovery(context).connectApp(app, host)
	}

	fun disconnect() {
		try {
			ProjectionState.carProjectionHost?.projection?.onProjectionStop(0)
			ProjectionState.carProjectionHost?.apply {
				context.unbindService(this)
			}
		} catch (e: Exception) {}
	}
	fun onDestroy() {
		disconnect()
		try {
			context.unbindService(carConnection)
		} catch (e: Exception) {}
		try {
			context.unbindService(callbackConnection)
		} catch (e: Exception) {}
	}
}