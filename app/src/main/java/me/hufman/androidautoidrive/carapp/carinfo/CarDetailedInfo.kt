package me.hufman.androidautoidrive.carapp.carinfo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.cds.CDSMetrics
import me.hufman.androidautoidrive.cds.CDSVehicleUnits
import me.hufman.androidautoidrive.phoneui.FlowUtils.addPlainUnit
import me.hufman.androidautoidrive.phoneui.FlowUtils.format

class CarDetailedInfo(carCapabilities: Map<String, Any?>, cdsMetrics: CDSMetrics) {
	// general car information

	// RHD is guessed, defaults to LHD
	// mainly used for driver/passenger window orientation
	val rightHandDrive = carCapabilities["type_steering"] == "right" ||
			carCapabilities["iva_STEERING_SIDE_LEFT"] == false ||
			carCapabilities["alignment-right"] == false

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
	val unitsSpeedLabel: Flow<String> = cdsMetrics.units.map {
		when (it.distanceUnits) {
			CDSVehicleUnits.Distance.Kilometers -> "kmph"
			CDSVehicleUnits.Distance.Miles -> "mph"
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

	val drivingMode = cdsMetrics.drivingMode
	val drivingGearLabel = cdsMetrics.drivingGearName.map { "${L.CARINFO_GEAR} $it"}

	// driving detail fields, as examples
	// real ones would need translated labels
	val accelContact = cdsMetrics.accelerator.format("%d%%").map { "Accel $it"}
	val accelEcoContact = cdsMetrics.acceleratorEco.format("%d%%").map { "AccelEco $it"}
	val clutchContact = cdsMetrics.clutch.map { "Clutch $it"}
	val brakeContact = cdsMetrics.brake.format("%d%%").map { "Brake $it"}
	val steeringAngle = cdsMetrics.steeringAngle.format("%.1f°").map { "Steering $it" }
	val speed = cdsMetrics.speedActual.format("%.1f").addPlainUnit(unitsSpeedLabel)
	val engineRpm =cdsMetrics.engineRpm.map { "$it RPM"}
	val heading = cdsMetrics.heading.map { heading ->
		val direction = CDSMetrics.compassDirection(heading)
		val arrow = CDSMetrics.compassArrow(heading)
		"$arrow $direction (${heading.toInt()}°)"
	}
	val gforces = cdsMetrics.accel.map { accel ->
		val lat = accel.first?.let {"%.2f".format(it/9.8)}?.plus("↔")
		val long = accel.second?.let {"%.2f".format(it/9.8)}?.plus("↕")
		"GForce $lat $long"
	}

	fun formatWindowState(name: String, state: CDSMetrics.WindowState): String = when(state.state) {
		CDSMetrics.WindowState.State.CLOSED -> "$name Closed"
		CDSMetrics.WindowState.State.TILTED -> "$name Tilted"
		CDSMetrics.WindowState.State.OPENED -> "$name Opened ${state.position}%"
	}.trimStart()
	fun Flow<CDSMetrics.WindowState>.format(name: String): Flow<String> = this.map {
		formatWindowState(name, it)
	}

	val sunroof = cdsMetrics.sunroof.format("Sunroof")
	val windowDriverFront = cdsMetrics.windowDriverFront.format("")
	val windowPassengerFront = cdsMetrics.windowPassengerFront.format("")
	val windowDriverRear = cdsMetrics.windowDriverRear.format("")
	val windowPassengerRear = cdsMetrics.windowPassengerRear.format("")

	// categories
	private val overviewFields: List<Flow<String>> = listOf(
			engineTemp, tempExterior,
			oilTemp, tempInterior,
			batteryTemp, tempExchanger,
			fuelLevelLabel, evLevelLabel,
			accBatteryLevelLabel
	)
	private val drivingFields: List<Flow<String>> = listOf(
			drivingMode, drivingGearLabel,
			accelContact, brakeContact,
			clutchContact, steeringAngle,
			speed, heading,
			gforces
	)
	private val windowFields: List<Flow<String>> = if (!rightHandDrive) {
		listOf(
				windowDriverFront, windowPassengerFront,
				windowDriverRear, windowPassengerRear
		)
	} else {
		listOf(
				windowPassengerFront, windowDriverFront,
				windowPassengerRear, windowDriverRear,
		)
	} + listOf(
			emptyFlow(), emptyFlow(),
			sunroof
	)
	val categories = LinkedHashMap<String, List<Flow<String>>>().apply {
		put(L.CARINFO_TITLE, overviewFields)
		put("Driving Details", drivingFields)
		put("Window Status", windowFields)

		// add more pages like this:
//		put("Driving Details", drivingFields)
	}
	val category = MutableStateFlow(categories.keys.first())
	val categoryFields: Flow<List<Flow<String>>> = category.map { categories[it] ?: emptyList() }
}