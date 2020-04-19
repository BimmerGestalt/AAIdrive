package me.hufman.carprojection.parcelables

import android.content.Context
import android.os.Parcelable
import me.hufman.carprojection.Gearhead
import java.lang.reflect.Constructor
import java.util.*

class CarInfo(val transport: Parcelable) {
	companion object {
		fun build(context: Context, carMake: String = "carMake", carModel: String = "carModel",
		          modelYear: String = "2020", vehicleId: String = "vin",
		          versionMajor: Int = 1, versionMinor: Int = 0, driverLeftSide: Boolean = true,
		          huMake: String = "huMake", huModel: String = "huModel",
		          huSwBuild: String = "1.0", huSwVersion: String = "1.0",
		          hideClock: Boolean = false, hidePhoneSignal: Boolean = false,
		          hideBatteryLevel: Boolean = false, canPlayNativeMediaDuringVr: Boolean = false): CarInfo {
			val constructor = Gearhead.getParcelableClass(context, "CarInfo").constructors.first {
				it.parameterTypes.size > 10
			} as Constructor<Parcelable>
			// the order that parameters are found in Android Auto 5.1.500644
			val stringTypes = LinkedList(mutableListOf(carMake, carModel, modelYear, vehicleId,
					huMake, huModel, huSwBuild, huSwVersion))
			val intTypes = LinkedList(mutableListOf(versionMajor, versionMinor, if (driverLeftSide) 0 else 1))
			val boolTypes = LinkedList(mutableListOf(hideClock, canPlayNativeMediaDuringVr, hidePhoneSignal, hideBatteryLevel))
			val constructorArgs = constructor.parameterTypes.map {
				when (it) {
					String::class.java -> stringTypes.poll() ?: ""
					Int::class.javaPrimitiveType -> intTypes.poll() ?: 0
					Boolean::class.javaPrimitiveType -> boolTypes.poll() ?: false
					else -> null
				}
			}.toTypedArray()
			return CarInfo(constructor.newInstance(*constructorArgs))
		}
	}
}