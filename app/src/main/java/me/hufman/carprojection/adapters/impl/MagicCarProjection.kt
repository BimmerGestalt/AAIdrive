package me.hufman.carprojection.adapters.impl

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import me.hufman.carprojection.Gearhead
import me.hufman.carprojection.adapters.CarProjection
import me.hufman.carprojection.parcelables.DrawingSpec
import me.hufman.carprojection.parcelables.InputFocusChangedEvent

class MagicCarProjection(context: Context, transport: IBinder) : CarProjection(context, transport) {
	private val onSetupFunction = proxy::class.java.methods.first {
		it.parameterTypes.size == 2 &&
		it.parameterTypes[0] == Gearhead.getInterface(context, "ICarProjectionCallback") &&
		it.parameterTypes[1] == Gearhead.getInterface(context, "ICar")
	}

	private val setIntent = proxy::class.java.methods.first {
		it.parameterTypes.size == 1 &&
		it.parameterTypes[0] == Intent::class.java
	}

	private val onConfigChanged = proxy::class.java.methods.first {
		it.parameterTypes.size == 3 &&
		it.parameterTypes[1] == Gearhead.getInterface(context, "DrawingSpec") &&
		it.parameterTypes[2] == Configuration::class.java
	}

	val onStart = proxy::class.java.methods.first {
		it.parameterTypes.size == 3 &&
			it.parameterTypes[0] == Gearhead.getInterface(context, "DrawingSpec") &&
			it.parameterTypes[1] == Intent::class.java &&
			it.parameterTypes[2] == Bundle::class.java
	}

	val onProjectionResume = proxy::class.java.methods.first {
		it.parameterTypes.size == 1 &&
		it.parameterTypes[0] == Int::class.javaPrimitiveType
	}

	val onInputFocusChange = proxy::class.java.methods.first {
		it.parameterTypes.size == 1 &&
		it.parameterTypes[0] == Gearhead.getInterface(context, "InputFocusChangedEvent")
	}

	val onKeyEvent = proxy::class.java.methods.first {
		it.parameterTypes.size == 1 &&
		it.parameterTypes[0] == KeyEvent::class.java
	}

	val onProjectionStop = proxy::class.java.methods.last {
		it.parameterTypes.size == 1 &&
		it.parameterTypes[0] == Integer::class.javaPrimitiveType
	}

	override fun onSetup(iCar: IBinder, iCarProjectionCallback: IBinder) {
		onSetupFunction.invoke(proxy, iCarProjectionCallback, iCar)
	}

	override fun onNewIntent(intent: Intent) {
		setIntent.invoke(proxy, intent)
	}

	override fun onConfigChanged(displayId: Int, drawingSpec: DrawingSpec, config: Configuration) {
		onConfigChanged.invoke(proxy, displayId, drawingSpec.transport, config)
	}

	override fun onProjectionStart(drawingSpec: DrawingSpec, intent: Intent, bundle: Bundle?) {
		onStart.invoke(proxy, drawingSpec.transport, intent, bundle)
	}

	override fun onProjectionResume(displayId: Int) {
		onProjectionResume.invoke(proxy, displayId)
	}

	override fun onInputFocusChange(inputFocusChangedEvent: InputFocusChangedEvent) {
		onInputFocusChange.invoke(proxy, inputFocusChangedEvent.transport)
	}

	override fun onKeyEvent(event: KeyEvent) {
		onKeyEvent.invoke(proxy, event)
	}

	override fun onProjectionStop(displayId: Int) {
		onProjectionStop.invoke(proxy, displayId)
	}
}