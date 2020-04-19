package me.hufman.carprojection.adapters

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.nhaarman.mockito_kotlin.withSettings
import me.hufman.carprojection.Gearhead
import me.hufman.carprojection.parcelables.CarInfo
import me.hufman.carprojection.parcelables.CarUiInfo
import org.mockito.Answers
import org.mockito.Mockito
import org.mockito.stubbing.Answer
import java.lang.reflect.Modifier.isAbstract

class ICarService: Service() {
	companion object {
		const val TAG = "FakeCar"
	}

	val carResponses = Answer<Any> { invocation ->
		if (isAbstract(invocation.method.modifiers)) {
			// expand parameter types for prettier printing
			val stringArguments = invocation.arguments.mapIndexed { index, argument ->
				when (argument) {
					is Bundle -> {
						try {
							argument.classLoader = Gearhead.getClassLoader(this)
							"Bundle(" + argument.keySet().map { "$it -> ${argument.get(it)}" }.joinToString(",") + ")"
						} catch (e: Exception) {
							argument.toString()
						}
					}
					else -> argument?.toString()
				}
			}
			val log = "Called ${invocation.method}(${stringArguments.joinToString(",")})"
			Log.d(TAG, log
					.replace("java.lang.", "")
					.replace("java.util.", "")
					.replace("com.google.android.gms.car.", "")
					.replace(" throws android.os.RemoteException", ""))

			// Figure out what sort of response to give
			val C = {name: String -> Gearhead.getParcelableClass(this, name) }
			when(invocation.method.returnType) {
				Boolean::class.javaPrimitiveType -> {
					if (invocation.arguments.getOrNull(0) == "rotary_use_focus_finder") {
						true
					} else {
						false
					}
				}
				Int::class.javaPrimitiveType -> {
					if (invocation.method.name == "o") {
						30  // fps
					} else {
						Answers.RETURNS_DEFAULTS.answer(invocation)
					}
				}
				List::class.java -> {
					if (invocation.arguments.getOrNull(0) == "rotary_use_focus_finder") {
						true
					} else {
						Answers.RETURNS_DEFAULTS.answer(invocation)
					}
				}
				C("CarInfo") -> CarInfo.build(this, hideClock = true, hidePhoneSignal = true).transport
				C("CarUiInfo") -> CarUiInfo.build(this, hasRotaryController = true, hasTouchScreen = false).transport
				else -> Answers.RETURNS_DEFAULTS.answer(invocation)
			}
		} else {
			Answers.CALLS_REAL_METHODS.answer(invocation)
		}
	}
	override fun onBind(p0: Intent?): IBinder? {
		try {
			val ICarStub = Gearhead.getInterface(this, "ICar\$Stub")
			Log.i(TAG, "Found ICarStub $ICarStub")

			val iCar = Mockito.mock(ICarStub, withSettings().defaultAnswer(carResponses).useConstructor()) as IBinder
			return iCar
		} catch (e: Exception) {
			Log.e(TAG, "Could not connect ICarStub", e)
			return null
		}
	}

}