package me.hufman.androidautoidrive.cds

import io.bimmergestalt.idriveconnectkit.CDS
import kotlinx.coroutines.flow.*
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsDouble
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsInt
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonObject
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonPrimitive
import kotlin.math.max

class CDSMetrics(val carInfo: CarInformation) {

	// unit conversions
	val units: Flow<CDSVehicleUnits> = carInfo.cachedCdsData.flow[CDS.VEHICLE.UNITS].map {
		CDSVehicleUnits.fromCdsProperty(it)
	}
	val unitsAverageConsumption: Flow<CDSVehicleUnits.Consumption> = carInfo.cachedCdsData.flow[CDS.DRIVING.AVERAGECONSUMPTION].map {
		CDSVehicleUnits.Consumption.fromValue(it.tryAsJsonObject("averageConsumption")?.getAsJsonPrimitive("unit")?.tryAsInt)
	}
	val unitsAverageSpeed: Flow<CDSVehicleUnits.Speed> = carInfo.cachedCdsData.flow[CDS.DRIVING.AVERAGESPEED].map {
		CDSVehicleUnits.Speed.fromValue(it.tryAsJsonObject("averageSpeed")?.getAsJsonPrimitive("unit")?.tryAsInt)
	}

	/** data points */
	// level
	val evLevel = carInfo.cachedCdsData.flow[CDS.SENSORS.SOCBATTERYHYBRID].mapNotNull {
		it.tryAsJsonPrimitive("SOCBatteryHybrid")?.tryAsDouble?.takeIf { it < 255 }
	}
	val fuelLevel = carInfo.cachedCdsData.flow[CDS.SENSORS.FUEL].mapNotNull {
		it.tryAsJsonObject("fuel")?.tryAsJsonPrimitive("tanklevel")?.tryAsDouble?.takeIf { it > 0 }
	}.combine(units) { value, units ->
		units.fuelUnits.fromCarUnit(value)
	}
	val accBatteryLevel = carInfo.cachedCdsData.flow[CDS.SENSORS.BATTERY].mapNotNull {
		it.tryAsJsonPrimitive("battery")?.tryAsDouble?.takeIf { it < 255 }
	}

	// range
	val evRange = carInfo.cachedCdsData.flow[CDS.DRIVING.DISPLAYRANGEELECTRICVEHICLE].mapNotNull {
		it.tryAsJsonPrimitive("displayRangeElectricVehicle")?.tryAsDouble?.takeIf { it < 4093 }
	}
	val evRangeOrZero = flow { emit(0.0); emitAll(evRange) }
	val fuelRange = carInfo.cachedCdsData.flow[CDS.SENSORS.FUEL].mapNotNull {
		it.tryAsJsonObject("fuel")?.tryAsJsonPrimitive("range")?.tryAsDouble
	}.combine(units) { value, units ->
		units.distanceUnits.fromCarUnit(value)
	}.combine(evRangeOrZero) { totalRange, evRange ->
		max(0.0, totalRange - evRange)
	}
	val totalRange = carInfo.cachedCdsData.flow[CDS.SENSORS.FUEL].mapNotNull {
		it.tryAsJsonObject("fuel")?.tryAsJsonPrimitive("range")?.tryAsDouble
	}.combine(units) { value, units ->
		units.distanceUnits.fromCarUnit(value)
	}

	// temp
	val engineTemp = carInfo.cachedCdsData.flow[CDS.ENGINE.TEMPERATURE].mapNotNull {
		it.tryAsJsonObject("temperature")?.tryAsJsonPrimitive("engine")?.tryAsDouble?.takeIf { it < 255 }
	}.combine(units) { value, units ->
		units.temperatureUnits.fromCarUnit(value)
	}
	val oilTemp = carInfo.cachedCdsData.flow[CDS.ENGINE.TEMPERATURE].mapNotNull {
		it.tryAsJsonObject("temperature")?.tryAsJsonPrimitive("oil")?.tryAsDouble?.takeIf { it < 255 }
	}.combine(units) { value, units ->
		units.temperatureUnits.fromCarUnit(value)
	}
	val batteryTemp = carInfo.cdsData.flow[CDS.SENSORS.BATTERYTEMP].mapNotNull {
		it.tryAsJsonPrimitive("batteryTemp")?.tryAsDouble?.takeIf { it < 255 }
	}.combine(units) { value, units ->
		units.temperatureUnits.fromCarUnit(value)
	}

	val tempInterior = carInfo.cdsData.flow[CDS.SENSORS.TEMPERATUREINTERIOR].mapNotNull {
		it.tryAsJsonPrimitive("temperatureInterior")?.tryAsDouble
	}.combine(units) { value, units ->
		units.temperatureUnits.fromCarUnit(value)
	}
	val tempExterior = carInfo.cdsData.flow[CDS.SENSORS.TEMPERATUREEXTERIOR].mapNotNull {
		it.tryAsJsonPrimitive("temperatureExterior")?.tryAsDouble
	}.combine(units) { value, units ->
		units.temperatureUnits.fromCarUnit(value)
	}
	val tempExchanger = carInfo.cdsData.flow[CDS.CLIMATE.ACSYSTEMTEMPERATURES].mapNotNull {
		it.tryAsJsonObject("ACSystemTemperatures")?.tryAsJsonPrimitive("heatExchanger")?.tryAsDouble
	}
	val tempEvaporator = carInfo.cdsData.flow[CDS.CLIMATE.ACSYSTEMTEMPERATURES].mapNotNull {
		it.tryAsJsonObject("ACSystemTemperatures")?.tryAsJsonPrimitive("evaporator")?.tryAsDouble
	}

	/*
		0 - Initialisierung -> initialization
		1 - Tractionmodus -> Traction mode
		2 - Komfortmodus -> Comfort mode
		3 - Basismodus -> Basic mode
		4 - Sportmodus -> Sports mode
		5 - Sportmodusplus -> Sport+
		6 - Racemodus -> Race
		7 - Ecopro ->EcoPro
		8 - Ecoproplus -> EcoPro+
		9 - Komfortmoduserweitert -> Comfort mode extended
		xx - Adaptive Mode
		15 - Unknown

		Some cars have "+" modes (sport+, comfort+) and some other the same modes are called "Individual".
	 */
	val drivingMode = carInfo.cdsData.flow[CDS.DRIVING.MODE].mapNotNull {
		val a = it.tryAsJsonPrimitive("mode")?.tryAsInt
		when (a) {
			null -> ""
			2 -> "Comfort"
			9 -> "Comfort+"
			3 -> "Basic"
			4 -> "Sport"
			5 -> "Sport+"
			6 -> "Race"
			7 -> "EcoPro"
			8 -> "EcoPro+"
			else -> "-$a-"
		}
	}
	val drivingModeSport = drivingMode.map {
		it == "Sport" || it == "Sport+" || it == "Race"
	}
	val drivingGear = carInfo.cdsData.flow[CDS.DRIVING.GEAR].mapNotNull {
		it.tryAsJsonPrimitive("gear")?.tryAsInt?.takeIf { it >0 }
	}
	val drivingGearName = drivingGear.combine(drivingModeSport) { gear, isSporty ->
		when (gear) {
			1 -> "N"
			2 -> "R"
			3 -> "P"
			in 5..16 -> if (isSporty) {
				"S${gear - 4}"
			} else {
				"D${gear - 4}"
			}
			else -> "-"
		}
	}

	val speedActual = carInfo.cdsData.flow[CDS.DRIVING.SPEEDACTUAL].mapNotNull {
		it.tryAsJsonPrimitive("speedActual")?.tryAsDouble
	}.combine(units) { value, units ->
		units.distanceUnits.fromCarUnit(value)
	}
	val speedDisplayed = carInfo.cdsData.flow[CDS.DRIVING.SPEEDDISPLAYED].mapNotNull {
		it.tryAsJsonPrimitive("speedDisplayed")?.tryAsDouble
		// probably doesn't need unit conversion
	}.combine(units) { value, units ->
		units.distanceUnits.fromCarUnit(value)
	}

	val accelerator = carInfo.cdsData.flow[CDS.DRIVING.ACCELERATORPEDAL].mapNotNull {
		it.tryAsJsonObject("acceleratorPedal")?.tryAsJsonPrimitive("position")?.tryAsDouble
	}
	val acceleratorEco = carInfo.cdsData.flow[CDS.DRIVING.ACCELERATORPEDAL].mapNotNull {
		it.tryAsJsonObject("acceleratorPedal")?.tryAsJsonPrimitive("ecoPosition")?.tryAsDouble
	}
	val brake = carInfo.cdsData.flow[CDS.DRIVING.BRAKECONTACT].mapNotNull {
		it.tryAsJsonPrimitive("brakeContact")?.tryAsDouble
	}
	val clutch = carInfo.cdsData.flow[CDS.DRIVING.CLUTCHPEDAL].mapNotNull {
		it.tryAsJsonObject("clutchPedal")?.tryAsJsonPrimitive("position")?.tryAsDouble
	}
	val steeringAngle = carInfo.cdsData.flow[CDS.DRIVING.STEERINGWHEEL].mapNotNull {
		it.tryAsJsonObject("steeringWheel")?.tryAsJsonPrimitive("angle")?.tryAsDouble
	}
}