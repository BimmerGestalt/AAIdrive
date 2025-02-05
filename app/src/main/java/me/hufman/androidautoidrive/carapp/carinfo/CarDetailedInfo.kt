package me.hufman.androidautoidrive.carapp.carinfo

import kotlinx.coroutines.flow.*
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.cds.CDSMetrics
import me.hufman.androidautoidrive.cds.CDSVehicleUnits
import me.hufman.androidautoidrive.phoneui.FlowUtils.addPlainUnit
import me.hufman.androidautoidrive.phoneui.FlowUtils.format
import kotlin.math.absoluteValue
import java.util.*
import java.time.format.*

class CarDetailedInfo(carCapabilities: Map<String, Any?>, cdsMetrics: CDSMetrics) {
	// general car information

	// RHD is guessed, defaults to LHD
	// mainly used for driver/passenger window orientation
	private val rightHandDrive = carCapabilities["type_steering"] == "right" ||
			carCapabilities["iva_STEERING_SIDE_LEFT"] == false ||
			carCapabilities["alignment-right"] == false

	// unit display
	private val unitsTemperatureLabel: Flow<String> = cdsMetrics.units.map {
		when (it.temperatureUnits) {
			CDSVehicleUnits.Temperature.CELCIUS -> L.CARINFO_UNIT_C
			CDSVehicleUnits.Temperature.FAHRENHEIT -> L.CARINFO_UNIT_F
		}
	}

	private val unitsDistanceLabel: Flow<String> = cdsMetrics.units.map {
		when (it.distanceUnits) {
			CDSVehicleUnits.Distance.Kilometers -> L.CARINFO_UNIT_KM
			CDSVehicleUnits.Distance.Miles -> L.CARINFO_UNIT_MI
		}
	}
	private val unitsSpeedLabel: Flow<String> = cdsMetrics.units.map {
		when (it.distanceUnits) {
			CDSVehicleUnits.Distance.Kilometers -> L.CARINFO_UNIT_KMPH
			CDSVehicleUnits.Distance.Miles -> L.CARINFO_UNIT_MPH
		}
	}

	private val unitsFuelLabel: Flow<String> = cdsMetrics.units.map {
		when (it.fuelUnits) {
			CDSVehicleUnits.Fuel.Liters -> L.CARINFO_UNIT_LITER
			CDSVehicleUnits.Fuel.Gallons_UK -> L.CARINFO_UNIT_GALUK
			CDSVehicleUnits.Fuel.Gallons_US -> L.CARINFO_UNIT_GALUS
		}
	}

	// data points
	private val evLevelLabel = cdsMetrics.evLevel.format("%.1f%%").map { "$it ${L.CARINFO_EV_BATTERY}"}

	private val fuelLevelLabel = cdsMetrics.fuelLevel.format("%.0f").addPlainUnit(unitsFuelLabel).map { "$it ${L.CARINFO_FUEL}"}

	private val accBatteryLevelLabel = cdsMetrics.accBatteryLevel.format("%.0f%%").map { "$it ${L.CARINFO_ACC_BATTERY}"}

	private val totalRangeLabel = cdsMetrics.totalRange.format("%.0f ").addPlainUnit(unitsDistanceLabel).map { "$it ${L.CARINFO_RANGE}"}

	private val engineTemp = cdsMetrics.engineTemp.format("%.0f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_ENGINE}"}
	private val oilTemp = cdsMetrics.oilTemp.format("%.0f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_OIL}"}
	private val batteryTemp = cdsMetrics.batteryTemp.format("%.0f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_BATTERY}"}

	private val tempInterior = cdsMetrics.tempInterior.format("%.1f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_INTERIOR}"}
	private val tempExterior = cdsMetrics.tempExterior.format("%.1f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_EXTERIOR}"}
	private val tempExchanger = cdsMetrics.tempExchanger.format("%.1f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_EXCHANGER}"}
	private val tempEvaporator = cdsMetrics.tempEvaporator.format("%.1f").addPlainUnit(unitsTemperatureLabel).map { "$it ${L.CARINFO_EVAPORATOR}"}

	private val drivingMode = cdsMetrics.drivingMode
	private val drivingGearLabel = cdsMetrics.drivingGearName.map { "${L.CARINFO_GEAR} $it"}

	// driving detail fields, as examples
	// real ones would need translated labels
	private val accelContact = cdsMetrics.accelerator.format("% 3d%%").map { "$it ${L.CARINFO_ACCEL}"}
//	val clutchContact = cdsMetrics.clutch.map { "Clutch $it"}
//	val brakeContact = cdsMetrics.brake.map { "Brake $it ${Integer.toBinaryString(it).padStart(8, '0')}"}
	private val steeringAngle = cdsMetrics.steeringAngle.map {
		val icon = if (it <= -0.5) {"→"} else if (it >= 0.5) {"←"} else {"↔"}
		"$icon % 3.0f°".format(it.absoluteValue)
	}.map { "$it ${L.CARINFO_STEERING}" }
	private val speed = cdsMetrics.speedActual.format("% 3.0f ").addPlainUnit(unitsSpeedLabel)
	private val speedGPS = cdsMetrics.speedGPS.format("% 3.0f ").addPlainUnit(unitsSpeedLabel).map { "$it ${L.CARINFO_SPEEDGPS}"}
	private val torque =cdsMetrics.torque.format("% 3.0f Nm").map { "$it ${L.CARINFO_TORQUE}" }
	private val engineRpm = cdsMetrics.engineRpm.map { "$it ${L.CARINFO_RPM}"}
	private val heading = cdsMetrics.heading.map { heading ->
		val direction = CDSMetrics.compassDirection(heading)
		val arrow = CDSMetrics.compassArrow(heading)
		"$arrow $direction (${heading.toInt()}°)"
	}
	private val gforces = cdsMetrics.accel.map { accel ->
		val lat = accel.first?.let {
			(if (it > 0.048) {"→"} else if (it < -0.048) {"←"} else {"↔"}) +  // 0.048 ≈ 0.005 * 9.81
				"%.2f".format(it.absoluteValue / 9.81)
		} ?: ""
		val long = accel.second?.let {
			(if (it > 0.048) {"↓"} else if (it < -0.048) {"↑"} else {"↕"}) +  // 0.048 ≈ 0.005 * 9.81
				"%.2f".format(it.absoluteValue / 9.81)
		} ?: ""
		"$lat $long${L.CARINFO_GFORCE}"
	}

	// advanced driving fields that aren't translated
	private val brakeState = cdsMetrics.brake.combine(cdsMetrics.parkingBrakeSet) { brakeContact, parkingBrakeSet ->
		// brakeContact is a bit coded value
		// bit 1 -> value 1 -> Brake pedal?
		// bit 2 -> value 2 -> Soft braking
		// bit 3 -> value 4 -> Medium / Strong braking
		// bit 4 -> value 8 -> Cruise control is braking
		// bit 5 -> value 16 -> Fullstop (when the vehicle is standing still, only when engine is running)

		// Choosing the "most interesting" bit in the right order because multiple bits might be set
		var brakeString = "Not braking"
		if (brakeContact?.and(8) == 8) {
			brakeString = "Cruise Control"
		} else if (brakeContact?.and(4) == 4) {
			brakeString = "Strong"
		} else if (brakeContact?.and(2) == 2) {
			brakeString = "Soft"
		}
		// Adding the parking brake info
		if (parkingBrakeSet || brakeContact == 16) {  // alternative value, when parkingBrakeSet is empty
			if (brakeString == "Not braking") {
				brakeString = "( ! )"
			} else {
				brakeString += " ( ! )"
			}
		}
		//brakeString = brakeContact!!.toString(2).padStart(8, '0')
		brakeString
	}
	private val clutchState = cdsMetrics.clutch.combine(cdsMetrics.gearboxType) { clutchContact, gearboxType ->
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
	private fun formatWindowState(name: String, state: CDSMetrics.WindowState): String = when(state.state) {
		CDSMetrics.WindowState.State.CLOSED -> "$name ${L.CARINFO_WINDOW_CLOSED}"
		CDSMetrics.WindowState.State.TILTED -> "$name ${L.CARINFO_WINDOW_TILTED}"
		CDSMetrics.WindowState.State.OPENED -> "$name ${L.CARINFO_WINDOW_OPENED} ${state.position}%"
	}.trimStart()
	fun Flow<CDSMetrics.WindowState>.format(name: String): Flow<String> = this.map {
		formatWindowState(name, it)
	}

	private val sunroof = cdsMetrics.sunroof.format("") //${L.CARINFO_SUNROOF}")
	private val windowDriverFront = cdsMetrics.windowDriverFront.format("")
	private val windowPassengerFront = cdsMetrics.windowPassengerFront.format("")
	private val windowDriverRear = cdsMetrics.windowDriverRear.format("")
	private val windowPassengerRear = cdsMetrics.windowPassengerRear.format("")

	// gps fields
	private val countryLabel = cdsMetrics.gpsCountry
	private val cityLabel = cdsMetrics.gpsCity //.map { "City: $it" }
	private val streetLabel = cdsMetrics.gpsStreet.combine(cdsMetrics.gpsHouseNumber) { street, houseNumber ->
		"$houseNumber $street".trim()
	}
	private val crossStreetLabel = cdsMetrics.gpsCrossStreet.map {
		if (it.isBlank()) "" else "and $it"
	}
	private val altitudeLabel = cdsMetrics.gpsAltitude.map { "${it}m ${L.CARINFO_GPSALTITUDE}" }
	private val latitudeLabel = cdsMetrics.gpsLat.map { "$it ${L.CARINFO_GPSLATITUDE}" }
	private val longitudeLabel = cdsMetrics.gpsLon.map { "$it ${L.CARINFO_GPSLONGITUDE}" }
//	private val rawHeading = cdsMetrics.rawHeading.format("%.0f Raw Heading")
	private val acCompressorActualPower = cdsMetrics.ACCompressorActualPower.format("%.0f").map { "$it ${L.CARINFO_COMPRESSORPOWER}" }
	private val acCompressorDualmode = cdsMetrics.ACCompressorDualMode.mapNotNull {
		when (it) {
			1 -> L.CARINFO_STATE_OFF
			2 -> L.CARINFO_STATE_ON
			else -> "?"
		}
	}.map { "${L.CARINFO_COMPRESSORDUALMODE}: $it" }
//	private val acActualTorque =cdsMetrics.ACCompressorActualTorque.format("%.0f").map { "$it Torque" }
	private val acCompressorState = cdsMetrics.ACCompressor.mapNotNull {
		when (it) {
			0 -> L.CARINFO_STATE_OFF
			1 -> L.CARINFO_STATE_ON
			else -> "?"
		}
	}
	private val acCompressorLevel = cdsMetrics.ACCompressorLevel.format("%.0f%%").map {"$it ${L.CARINFO_COMPRESSORLEVEL}"}

	private val distNextDestLabel = cdsMetrics.navDistNext.format("%.1f ").addPlainUnit(unitsDistanceLabel).map { "$it ${L.CARINFO_NAV_DISTANCE}"}
	private val distOrCityLabel = combine(cdsMetrics.navGuidanceStatus, cityLabel, distNextDestLabel, cdsMetrics.carDateTime) { status, city, distance, carTime ->
		if (status != 0 && ((carTime.second % 30 < 15) || city == "")) distance else city
	}
	private val timeOrStreetLabel = combine(cdsMetrics.navGuidanceStatus, streetLabel, cdsMetrics.navTimeNext, cdsMetrics.carDateTime) { status, street, timeLeft, carTime ->
		if (status != 0 && ((carTime.second % 30 < 15) || street == ""))
			String.format("%d:%02d→", timeLeft / 60, timeLeft % 60) +
					carTime.plusMinutes(timeLeft).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) +
					" ${L.CARINFO_NAV_ETA}"
		else street
	}

	// categories
	private val sportFields: List<Flow<String>> = listOf(
		engineTemp, oilTemp,
		accelContact, brakeState,
		engineRpm, torque,
		drivingGearLabel, steeringAngle,
		gforces
	)
	private val overviewFields: List<Flow<String>> = listOf(
		engineTemp, tempExterior,
		oilTemp, tempInterior,
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
			flowOf(L.CARINFO_SUNROOF), sunroof
		)
	private val acFields: List<Flow<String>> = listOf(
		tempExterior, tempInterior,
		tempEvaporator, tempExchanger,
		flowOf(L.CARINFO_COMPRESSOR), emptyFlow(),
		acCompressorState, acCompressorDualmode,
		acCompressorLevel, acCompressorActualPower
	)
	private val travelFields: List<Flow<String>> = listOf(
		speed, drivingGearLabel,
		altitudeLabel, tempExterior,
		accBatteryLevelLabel, tempInterior,
		totalRangeLabel, fuelLevelLabel,
		distOrCityLabel, timeOrStreetLabel
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
		put (L.CARINFO_TITLE_AC, acFields)
		put(L.CARINFO_TITLE_WINDOWS, windowFields)
		put(L.CARINFO_TITLE_TRAVEL, travelFields)
	}
	private val allCategories = basicCategories + advancedCategories
	val category = MutableStateFlow(allCategories.keys.first())
	val categoryFields: Flow<List<Flow<String>>> = category.map { allCategories[it] ?: emptyList() }
}
