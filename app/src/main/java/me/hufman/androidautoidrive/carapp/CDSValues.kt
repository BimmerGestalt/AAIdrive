package me.hufman.androidautoidrive.carapp

import com.google.gson.JsonObject
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsInt
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonObject
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonPrimitive
import java.util.*

enum class CDSVehicleLanguage(val value: Int, val locale: Locale) {
	NONE(0, Locale.ROOT),
	DE(1, Locale("DE", "")),
	EN_UK(2, Locale("EN", "UK")),
	EN_US(3, Locale("EN", "US")),
	ES_ES(4, Locale("ES", "ES")),
	IT(5, Locale("IT", "")),
	FR_FR(6, Locale("FR", "FR")),
	NL_BE(7, Locale("NL", "BE")),
	NL_NL(8, Locale("NL", "NL")),
	AR(9, Locale("AR", "")),
	ZH_TW(10, Locale("ZH", "TW")),
	ZH_CN(11, Locale("ZH", "CN")),
	KO(12, Locale("KO", "")),
	JA(13, Locale("JA", "")),
	RU(14, Locale("RU", "")),
	FR_CA(15, Locale("FR", "CA")),
	ES_MX(16, Locale("ES", "MX")),
	PT(17, Locale("PT", "")),
	PL(18, Locale("PL", "")),
	EL(19, Locale("EL", "")),
	TR(20, Locale("TR", "")),
	HU(21, Locale("HU", "")),
	RO(22, Locale("RO", "")),
	SV(23, Locale("SV", "")),
	PT_BR(24, Locale("PT", "BR")),
	SK(27, Locale("SK", "")),
	CS(28, Locale("CS", "")),
	SL(29, Locale("SL", "")),
	DA(30, Locale("DA", "")),
	NO(31, Locale("NO", "")),
	FI(32, Locale("FI", "")),
	ID(33, Locale("ID", "")),
	TH(34, Locale("TH", "")),
	INVALID(255, Locale.ROOT);

	companion object {
		fun fromValue(value: Int?): CDSVehicleLanguage {
			return values().firstOrNull {
				it.value == value
			} ?: INVALID
		}

		fun fromCdsProperty(value: JsonObject?): CDSVehicleLanguage {
			return fromValue(value?.get("language")?.asInt)
		}
	}
}

class CDSVehicleUnits(val consumptionUnits: Consumption, val distanceUnits: Distance, val fuelUnits: Fuel,
                      val temperatureUnits: Temperature) {
	enum class Consumption {
		L_100km,
		MPG_UK,
		MPG_US,
		KM_L;

		fun fromCarUnit(value: Number): Double {
			return when (this) {
				L_100km -> value.toDouble()
				MPG_UK -> value.toDouble() * 0.00354006189
				MPG_US -> value.toDouble() * 0.00425143707
				KM_L -> 100 / value.toDouble()
			}
		}

		companion object {
			fun fromValue(value: Int?): Consumption {
				return when (value) {
					2 -> MPG_UK
					3 -> MPG_US
					4 -> KM_L
					else -> L_100km
				}
			}
		}
	}

	enum class Distance {
		Kilometers,
		Miles;

		fun fromCarUnit(value: Number): Double {
			return when (this) {
				Kilometers -> value.toDouble()
				Miles -> value.toDouble() * 0.621371
			}
		}

		companion object {
			fun fromValue(value: Int?): Distance {
				return when (value) {
					2 -> Miles
					else -> Kilometers
				}
			}
		}
	}

	enum class Fuel {
		Liters,
		Gallons_UK,
		Gallons_US;

		fun fromCarUnit(value: Number): Double {
			return when (this) {
				Liters -> value.toDouble()
				Gallons_UK -> value.toDouble() * 0.219969204701183
				Gallons_US -> value.toDouble() * 0.264172
			}
		}

		companion object {
			fun fromValue(value: Int?): Fuel {
				return when (value) {
					2 -> Gallons_UK
					3 -> Gallons_US
					else -> Liters
				}
			}
		}
	}

	// Speed isn't directly in vehicle.units, and must be constructed manually from vehicle.unitSpeed or averageSpeed.unit
	enum class Speed {
		KMPH,
		MPH;

		fun fromCarUnit(value: Number): Double {
			return when (this) {
				KMPH -> value.toDouble()
				MPH -> value.toDouble() * 1.609344
			}
		}

		companion object {
			fun fromValue(value: Int?): Speed {
				return when (value) {
					2 -> MPH
					else -> KMPH
				}
			}
		}
	}

	enum class Temperature {
		CELCIUS,
		FAHRENHEIT;

		fun fromCarUnit(value: Number): Double {
			return when (this) {
				CELCIUS -> value.toDouble()
				FAHRENHEIT -> value.toDouble() * 1.8 + 32
			}
		}

		companion object {
			fun fromValue(value: Int?): Temperature {
				return when (value) {
					2 -> FAHRENHEIT
					else -> CELCIUS
				}
			}
		}
	}

	companion object {
		val UNKNOWN = CDSVehicleUnits(
				Consumption.fromValue(null),
				Distance.fromValue(null),
				Fuel.fromValue(null),
				Temperature.fromValue(null)
		)
		fun fromCdsProperty(value: JsonObject?): CDSVehicleUnits {
			val units = value?.tryAsJsonObject("units")
			return CDSVehicleUnits(
					Consumption.fromValue(units?.tryAsJsonPrimitive("consumption")?.tryAsInt),
					Distance.fromValue(units?.tryAsJsonPrimitive("distance")?.tryAsInt),
					Fuel.fromValue(units?.tryAsJsonPrimitive("fuel")?.tryAsInt),
					Temperature.fromValue(units?.tryAsJsonPrimitive("temperature")?.tryAsInt)
			)
		}
	}
}