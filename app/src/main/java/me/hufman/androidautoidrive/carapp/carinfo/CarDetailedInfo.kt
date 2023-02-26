package me.hufman.androidautoidrive.carapp.carinfo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.cds.CDSMetrics
import me.hufman.androidautoidrive.cds.CDSVehicleUnits
import me.hufman.androidautoidrive.phoneui.FlowUtils.addPlainUnit
import me.hufman.androidautoidrive.phoneui.FlowUtils.format

class CarDetailedInfo(cdsMetrics: CDSMetrics) {

	// unit display
	val unitsTemperatureLabel: Flow<String> = cdsMetrics.units.map {
		when (it.temperatureUnits) {
			CDSVehicleUnits.Temperature.CELCIUS -> L.CARINFO_UNIT_C
			CDSVehicleUnits.Temperature.FAHRENHEIT -> L.CARINFO_UNIT_F
		}
	}

	val unitsDistanceLabel: Flow<String> = cdsMetrics.units.map {
		when (it.distanceUnits) {
			CDSVehicleUnits.Distance.Kilometers -> L.CARINFO_UNIT_KM
			CDSVehicleUnits.Distance.Miles -> L.CARINFO_UNIT_MI
		}
	}

	val unitsFuelLabel: Flow<String> = cdsMetrics.units.map {
		when (it.fuelUnits) {
			CDSVehicleUnits.Fuel.Liters -> L.CARINFO_UNIT_LITER
			CDSVehicleUnits.Fuel.Gallons_UK -> L.CARINFO_UNIT_GALUK
			CDSVehicleUnits.Fuel.Gallons_US -> L.CARINFO_UNIT_GALUS
		}
	}

	// data points
	val evLevelLabel = cdsMetrics.evLevel.format("%.1f%%").map { "$it ${L.CARINFO_EV_BATTERY}"}

	val fuelLevelLabel = cdsMetrics.fuelLevel.format("%.1f").addPlainUnit(unitsFuelLabel).map { "$it ${L.CARINFO_FUEL}"}

	val accBatteryLevelLabel = cdsMetrics.accBatteryLevel.format("%.0f%%").map { "$it ${L.CARINFO_ACC_BATTERY}"}

	val engineTemp = cdsMetrics.engineTemp.format("%.0f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_ENGINE}"}
	val oilTemp = cdsMetrics.oilTemp.format("%.0f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_OIL}"}
	val batteryTemp = cdsMetrics.batteryTemp.format("%.0f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_BATTERY}"}

	val tempInterior = cdsMetrics.tempInterior.format("%.1f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_INTERIOR}"}
	val tempExterior = cdsMetrics.tempExterior.format("%.1f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_EXTERIOR}"}
	val tempExchanger = cdsMetrics.tempExchanger.format("%.1f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_EXCHANGER}"}

	val drivingGearLabel = cdsMetrics.drivingGearName.map { "${L.CARINFO_GEAR} $it"}
}