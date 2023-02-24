package me.hufman.androidautoidrive.carapp.carinfo

import io.bimmergestalt.idriveconnectkit.CDS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.cds.CDSVehicleUnits
import me.hufman.androidautoidrive.cds.flow
import me.hufman.androidautoidrive.phoneui.FlowUtils.addPlainUnit
import me.hufman.androidautoidrive.phoneui.FlowUtils.format
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsDouble
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsInt
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonObject
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonPrimitive

class CarDetailedInfo(carInfoOverride: CarInformation? = null) {

	private val carInfo = carInfoOverride ?: CarInformation()

	// unit conversions
	val units: Flow<CDSVehicleUnits> = carInfo.cachedCdsData.flow[CDS.VEHICLE.UNITS].map {
		CDSVehicleUnits.fromCdsProperty(it)
	}

	val unitsTemperatureLabel: Flow<String> = units.map {
		when (it.temperatureUnits) {
			CDSVehicleUnits.Temperature.CELCIUS -> L.CARINFO_UNIT_C
			CDSVehicleUnits.Temperature.FAHRENHEIT -> L.CARINFO_UNIT_F
		}
	}

	val unitsDistanceLabel: Flow<String> = units.map {
		when (it.distanceUnits) {
			CDSVehicleUnits.Distance.Kilometers -> L.CARINFO_UNIT_KM
			CDSVehicleUnits.Distance.Miles -> L.CARINFO_UNIT_MI
		}
	}

	val unitsFuelLabel: Flow<String> = units.map {
		when (it.fuelUnits) {
			CDSVehicleUnits.Fuel.Liters -> L.CARINFO_UNIT_LITER
			CDSVehicleUnits.Fuel.Gallons_UK -> L.CARINFO_UNIT_GALUK
			CDSVehicleUnits.Fuel.Gallons_US -> L.CARINFO_UNIT_GALUS
		}
	}

	// data points
	val evLevel = carInfo.cachedCdsData.flow[CDS.SENSORS.SOCBATTERYHYBRID].mapNotNull {
		it.tryAsJsonPrimitive("SOCBatteryHybrid")?.tryAsDouble?.takeIf { it < 255 }
	}
	val evLevelLabel = evLevel.format("%.1f%%").map { "$it ${L.CARINFO_EV_BATTERY}"}

	val fuelLevel = carInfo.cachedCdsData.flow[CDS.SENSORS.FUEL].mapNotNull {
		it.tryAsJsonObject("fuel")?.tryAsJsonPrimitive("tanklevel")?.tryAsDouble?.takeIf { it > 0 }
	}.combine(units) { value, units ->
		units.fuelUnits.fromCarUnit(value)
	}
	val fuelLevelLabel = fuelLevel.format("%.1f").addPlainUnit(unitsFuelLabel).map { "$it ${L.CARINFO_FUEL}"}

	val accBatteryLevel = carInfo.cachedCdsData.flow[CDS.SENSORS.BATTERY].mapNotNull {
		it.tryAsJsonPrimitive("battery")?.tryAsDouble?.takeIf { it < 255 }
	}
	val accBatteryLevelLabel = accBatteryLevel.format("%.0f%%").map { "$it ${L.CARINFO_ACC_BATTERY}"}

	val engineTemp = carInfo.cachedCdsData.flow[CDS.ENGINE.TEMPERATURE].mapNotNull {
		it.tryAsJsonObject("temperature")?.tryAsJsonPrimitive("engine")?.tryAsDouble
	}.combine(units) { value, units ->
		units.temperatureUnits.fromCarUnit(value)
	}.format("%.0f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_ENGINE}"}
	val oilTemp = carInfo.cachedCdsData.flow[CDS.ENGINE.TEMPERATURE].mapNotNull {
		it.tryAsJsonObject("temperature")?.tryAsJsonPrimitive("oil")?.tryAsDouble
	}.combine(units) { value, units ->
		units.temperatureUnits.fromCarUnit(value)
	}.format("%.0f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_OIL}"}
	val batteryTemp = carInfo.cachedCdsData.flow[CDS.SENSORS.BATTERYTEMP].mapNotNull {
		it.tryAsJsonPrimitive("batteryTemp")?.tryAsDouble
	}.combine(units) { value, units ->
		units.temperatureUnits.fromCarUnit(value)
	}.format("%.0f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_BATTERY}"}

	val tempInterior = carInfo.cdsData.flow[CDS.SENSORS.TEMPERATUREINTERIOR].mapNotNull {
		it.tryAsJsonPrimitive("temperatureInterior")?.tryAsDouble
	}.combine(units) { value, units ->
		units.temperatureUnits.fromCarUnit(value)
	}.format("%.1f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_INTERIOR}"}
	val tempExterior = carInfo.cdsData.flow[CDS.SENSORS.TEMPERATUREEXTERIOR].mapNotNull {
		it.tryAsJsonPrimitive("temperatureExterior")?.tryAsDouble
	}.combine(units) { value, units ->
		units.temperatureUnits.fromCarUnit(value)
	}.format("%.1f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_EXTERIOR}"}
	val tempExchanger = carInfo.cdsData.flow[CDS.CLIMATE.ACSYSTEMTEMPERATURES].mapNotNull {
		it.tryAsJsonObject("ACSystemTemperatures")?.tryAsJsonPrimitive("heatExchanger")?.tryAsDouble
	}.format("%.1f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_EXCHANGER}"}

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
		it.tryAsJsonPrimitive("gear")?.tryAsInt?.takeIf { it >0 } ?: 255
	}
	val drivingGearLabel = drivingGear.combine(drivingModeSport) { gear, isSporty ->
		when (gear) {
			1 -> "N"
			2 -> "R"
			3 -> "P"
			in 5..16 -> if (isSporty) {
				"S ${gear - 4}"
			} else {
				"D ${gear - 4}"
			}
			else -> "-"
		}
	}.map { "${L.CARINFO_GEAR} $it"}

}