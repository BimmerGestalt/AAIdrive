package me.hufman.carprojection.adapters

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import me.hufman.carprojection.Gearhead
import me.hufman.carprojection.adapters.impl.MagicCarProjection
import me.hufman.carprojection.parcelables.DrawingSpec
import me.hufman.carprojection.parcelables.InputFocusChangedEvent

abstract class CarProjection(val context: Context, transport: IBinder) {
	val proxy = Gearhead.createInterface(context, "ICarProjection\$Stub\$Proxy", transport)

	companion object {
		fun getInstance(context: Context, transport: IBinder): CarProjection {
			val version = Gearhead.getPackageInfo(context)
			// different CarProject adapters could handle different versions
			return MagicCarProjection(context, transport)
		}
	}

	abstract fun onSetup(iCar: IBinder, iCarProjectionCallback: IBinder)
	abstract fun onNewIntent(intent: Intent)
	abstract fun onConfigChanged(displayId: Int, drawingSpec: DrawingSpec, config: Configuration)
	abstract fun onProjectionStart(drawingSpec: DrawingSpec, intent: Intent, bundle: Bundle?)
	abstract fun onProjectionResume(displayId: Int)
	abstract fun onInputFocusChange(inputFocusChangedEvent: InputFocusChangedEvent)
	abstract fun onKeyEvent(event: KeyEvent)
	// technically there's also an onProjectionPause(displayId: Int)
	abstract fun onProjectionStop(displayId: Int)
}