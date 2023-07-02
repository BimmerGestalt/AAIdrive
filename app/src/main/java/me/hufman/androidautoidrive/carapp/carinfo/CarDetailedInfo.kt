package me.hufman.androidautoidrive.carapp.carinfo

import kotlinx.coroutines.flow.*
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.cds.CDSMetrics
import me.hufman.androidautoidrive.cds.CDSVehicleUnits
import me.hufman.androidautoidrive.phoneui.FlowUtils.addPlainUnit
import me.hufman.androidautoidrive.phoneui.FlowUtils.format
import kotlin.math.absoluteValue

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
			CDSVehicleUnits.Distance.Kilometers -> L.CARINFO_UNIT_KMPH
			CDSVehicleUnits.Distance.Miles -> L.CARINFO_UNIT_MPH
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
	val tempEvaporator = cdsMetrics.tempEvaporator.format("%.1f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_EVAPORATOR}"}

	val drivingMode = cdsMetrics.drivingMode
	val drivingGearLabel = cdsMetrics.drivingGearName.map { "${L.CARINFO_GEAR} $it"}

	// driving detail fields, as examples
	// real ones would need translated labels
	val accelContact = cdsMetrics.accelerator.format("%d%%").map { "$it ${L.CARINFO_ACCEL}"}
//	val clutchContact = cdsMetrics.clutch.map { "Clutch $it"}
//	val brakeContact = cdsMetrics.brake.map { "Brake $it ${Integer.toBinaryString(it).padStart(8, '0')}"}
	val steeringAngle = cdsMetrics.steeringAngle.map {
		val icon = if (it < 0) {"→"} else if (it > 0) {"←"} else {""}
		"$icon %.0f°".format(it.absoluteValue)
	}.map { "$it ${L.CARINFO_STEERING}" }
	val speed = cdsMetrics.speedActual.format("%.0f").addPlainUnit(unitsSpeedLabel)
	val speedGPS = cdsMetrics.speedGPS.format("%.0f").addPlainUnit(unitsSpeedLabel)
	val torque =cdsMetrics.torque.format("%.0fNM").map { "$it ${L.CARINFO_TORQUE}" }
	val engineRpm = cdsMetrics.engineRpm.map { "$it ${L.CARINFO_RPM}"}
	val heading = cdsMetrics.heading.map { heading ->
		val direction = CDSMetrics.compassDirection(heading)
		val arrow = CDSMetrics.compassArrow(heading)
		"$arrow $direction (${heading.toInt()}°)"
	}
	val gforces = cdsMetrics.accel.map { accel ->
		val lat = accel.first?.let { "↔%.2f".format(it / 9.8) } ?: ""
		val long = accel.second?.let { "↕%.2f".format(it / 9.8) } ?: ""
		"$lat $long ${L.CARINFO_GFORCE}"
	}
	val gforceLat = cdsMetrics.accel.map { accel ->
		val lat = accel.first?.let {"↔%.2f".format(it/9.8)} ?: ""
		"$lat"
	}
	val gforceLong = cdsMetrics.accel.map { accel ->
		val long = accel.second?.let {"↕%.2f".format(it/9.8)} ?: ""
		"$long ${L.CARINFO_GFORCE}"
	}

	// advanced driving fields that aren't translated
	val brakeState = cdsMetrics.brake.combine(cdsMetrics.parkingBrakeSet) { brakeContact, parkingBrakeSet ->
		// brakeContact is a bit coded value
		// bit 1 -> value 1 -> Brake pedal?
		// bit 2 -> value 2 -> Soft braking
		// bit 3 -> value 4 -> Medium / Strong braking
		// bit 4 -> value 8 -> Cruise control is braking
		// bit 5 -> value 16 -> Fullstop (when the vehicle is standing still, only when engine is running)

		// Choosing the "most interesting" bit in the right order because multiple bits might be set
		var brakeString = "Not braking"
		val brakeContact = brakeContact ?: 0
		if (brakeContact.and(8) == 8) {
			brakeString = "Cruise Control"
		} else if (brakeContact.and(4) == 4) {
			brakeString = "Strong"
		} else if (brakeContact.and(2) == 2) {
			brakeString = "Soft"
		}
		// Adding the parking brake info
		if (parkingBrakeSet) {
			if (brakeString == "Not braking") {
				brakeString = "( ! )"
			} else {
				brakeString += " ( ! )"
			}
		}
		brakeString
	}
	val clutchState = cdsMetrics.clutch.combine(cdsMetrics.gearboxType) { clutchContact, gearboxType ->
		if (gearboxType == 1) {
			//automatic transmission with torque converter
			when (clutchContact) {
				null -> ""
				0 -> "Coupled"
				1 -> "Sailing"
				2 -> "Uncoupled"
				3 -> "Open"
				else -> "-$clutchContact-"
			}
		} else {
			// unknown transmission type -> i don't know the values of clutchPedalPosition and gearboxType
			// for manual transmissions, dual clutch transmissions and electric vehicles
			"-$clutchContact-"
		}
	}

	// windows are not translated
	fun formatWindowState(name: String, state: CDSMetrics.WindowState): String = when(state.state) {
		CDSMetrics.WindowState.State.CLOSED -> "$name ${L.CARINFO_WINDOW_CLOSED}"
		CDSMetrics.WindowState.State.TILTED -> "$name ${L.CARINFO_WINDOW_TILTED}"
		CDSMetrics.WindowState.State.OPENED -> "$name ${L.CARINFO_WINDOW_OPENED} ${state.position}%"
	}.trimStart()
	fun Flow<CDSMetrics.WindowState>.format(name: String): Flow<String> = this.map {
		formatWindowState(name, it)
	}

	val sunroof = cdsMetrics.sunroof.format("${L.CARINFO_SUNROOF}")
	val windowDriverFront = cdsMetrics.windowDriverFront.format("")
	val windowPassengerFront = cdsMetrics.windowPassengerFront.format("")
	val windowDriverRear = cdsMetrics.windowDriverRear.format("")
	val windowPassengerRear = cdsMetrics.windowPassengerRear.format("")

	// gps fields
	val countryLabel = cdsMetrics.gpsCountry
	val cityLabel = cdsMetrics.gpsCity //.map { "City: $it" }
	val streetLabel = cdsMetrics.gpsStreet.combine(cdsMetrics.gpsHouseNumber) { street, houseNumber ->
		"$houseNumber $street".trim()
	}
	val crossStreetLabel = cdsMetrics.gpsCrossStreet.map {
		if (it.isBlank()) "" else "and $it"
	}
	val altitudeLabel = cdsMetrics.gpsAltitude.map { "${it}m ${L.CARINFO_GPSALTITUDE}" }
	val latitudeLabel = cdsMetrics.gpsLat.map { "$it ${L.CARINFO_GPSLATITUDE}" }
	val longitudeLabel = cdsMetrics.gpsLon.map { "$it ${L.CARINFO_GPSLONGITUDE}" }
//	val rawHeading = cdsMetrics.rawHeading.format("%.0f Raw Heading")
   val ACCompressorActualPower =cdsMetrics.ACCompressorActualPower.format("%.0f").map { "$it ${L.CARINFO_COMPRESSORPOWER}" }
   val ACCompressorDualmode =cdsMetrics.ACCompressorDualMode.mapNotNull {
		when (it) {
			1 -> "${L.CARINFO_STATE_OFF}"
			2 -> "${L.CARINFO_STATE_ON}"
			else -> "?"
		}
	}.map { "${L.CARINFO_COMPRESSORDUALMODE}: $it" }
//	val ACActualTorque =cdsMetrics.ACCompressorActualTorque.format("%.0f").map { "$it Torque" }
	val ACCompressorState = cdsMetrics.ACCompressor.mapNotNull {
				when (it) {
					0 -> "${L.CARINFO_STATE_OFF}"
					1 -> "${L.CARINFO_STATE_ON}"
					else -> "?"
				}
	}
	val ACCompressorLevel = cdsMetrics.ACCompressorLevel.format("%.0f%%").map {"$it ${L.CARINFO_COMPRESSORLEVEL}"}

	// categories
	private val sportFields: List<Flow<String>> = listOf(
			engineTemp, oilTemp,
			accelContact, brakeState,
			engineRpm, torque,
			drivingGearLabel, steeringAngle,
			gforceLat, gforceLong
	)
	private val overviewFields: List<Flow<String>> = listOf(
			engineTemp, tempExterior,
			oilTemp, tempInterior,
			tempEvaporator, tempExchanger,
			fuelLevelLabel, evLevelLabel,
			accBatteryLevelLabel,batteryTemp
	)
	private val drivingFields: List<Flow<String>> = listOf(
			drivingMode, drivingGearLabel,
			accelContact, steeringAngle,
			speed, heading,
			gforces, engineRpm
	)
	private val drivingAdvancedFields: List<Flow<String>> = listOf(
			drivingMode, drivingGearLabel,
			accelContact, brakeState,
			clutchState, steeringAngle,
			speed, heading,
			gforces, engineRpm
	)
	private val drivingPerformanceFields: List<Flow<String>> = listOf(
			drivingMode, drivingGearLabel,
			accelContact, steeringAngle,
			speed, torque,
			gforces, heading,
			engineTemp, oilTemp
	)
	private val gpsFields: List<Flow<String>> = listOf(
			countryLabel, heading,
			cityLabel, altitudeLabel,
			streetLabel, crossStreetLabel,
			latitudeLabel, longitudeLabel,
			speedGPS
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
	private val ACFields: List<Flow<String>> = listOf(
			tempExterior, tempInterior,
			tempEvaporator, tempExchanger,
			flowOf(L.CARINFO_COMPRESSOR),emptyFlow(),
			ACCompressorState, ACCompressorDualmode,
			ACCompressorLevel, ACCompressorActualPower,
	)

	val basicCategories = LinkedHashMap<String, List<Flow<String>>>().apply {
		put(L.CARINFO_TITLE, overviewFields)
		put(L.CARINFO_TITLE_DRIVING, drivingFields)
		put(L.CARINFO_TITLE_SPORT, sportFields)
	}
	val advancedCategories = LinkedHashMap<String, List<Flow<String>>>().apply {
		put(L.CARINFO_TITLE, overviewFields)
		put(L.CARINFO_TITLE_DRIVING + " ", drivingAdvancedFields)   // slightly different key for the allCategories

		// add more pages like this:
		put (L.CARINFO_TITLE_SPORT + " ", drivingPerformanceFields) // slightly different key for the allCategories
		put(L.CARINFO_TITLE_GPS, gpsFields)
		put (L.CARINFO_TITLE_AC, ACFields)
		put(L.CARINFO_TITLE_WINDOWS, windowFields)
	}
	val allCategories = basicCategories + advancedCategories
	val category = MutableStateFlow(allCategories.keys.first())
	val categoryFields: Flow<List<Flow<String>>> = category.map { allCategories[it] ?: emptyList() }
}