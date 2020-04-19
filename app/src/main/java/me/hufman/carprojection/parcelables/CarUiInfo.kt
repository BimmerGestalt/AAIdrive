package me.hufman.carprojection.parcelables

import android.content.Context
import android.graphics.Point
import android.os.Parcelable
import me.hufman.carprojection.Gearhead
import java.lang.reflect.Constructor
import java.util.*

class CarUiInfo(val transport: Parcelable) {
	companion object {
		fun build(context: Context, hasRotaryController: Boolean = false, hasTouchScreen: Boolean = false,
		          hasSearchButton: Boolean = false, hasTouchpadForUiNavigation: Boolean = false, hasDpad: Boolean = false,
		          touchscreenType: Int = 0, isTouchpadUiAbsolute: Boolean = false,
		          touchpadMoveThresholdPx: Int = 0, touchpadMultimoveThresholdPx: Int = 0): CarUiInfo {
			val constructor = Gearhead.getParcelableClass(context, "CarUiInfo").constructors.first {
				it.parameterTypes.size > 6
			} as Constructor<Parcelable>
			// the order that parameters are found in Android Auto 5.1.500644
			val intTypes = LinkedList(mutableListOf(touchscreenType, touchpadMoveThresholdPx, touchpadMultimoveThresholdPx))
			val boolTypes = LinkedList(mutableListOf(hasRotaryController, hasTouchScreen, hasSearchButton,
					hasTouchpadForUiNavigation, hasDpad, isTouchpadUiAbsolute))
			val constructorArgs = constructor.parameterTypes.map {
				when (it) {
					Int::class.javaPrimitiveType -> intTypes.poll() ?: 0
					Boolean::class.javaPrimitiveType -> boolTypes.poll() ?: false
					Array<Int>::class.java -> arrayOf(40, 50)
					Point::class.java -> Point(40, 50)
					else -> null
				}
			}.toTypedArray()
			return CarUiInfo(constructor.newInstance(*constructorArgs))
		}
	}
}