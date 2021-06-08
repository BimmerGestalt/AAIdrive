package me.hufman.androidautoidrive.phoneui.viewmodels

import me.hufman.androidautoidrive.carapp.CDSVehicleUnits
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsDouble
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonObject
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonPrimitive
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsString

import android.content.Context
import android.net.Uri
import android.os.Handler
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.gson.JsonObject
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.liveData
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.addUnit
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.combine
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.format
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.map
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsInt
import me.hufman.idriveconnectionkit.CDS
import java.lang.Exception
import java.lang.Math.round
import java.text.DateFormat
import java.util.*
import kotlin.math.max

class CarDrivingStatsModel(carInfoOverride: CarInformation? = null, val showAdvancedSettings: BooleanLiveSetting): ViewModel() {
	companion object {
		val CACHED_KEYS = setOf(
				CDS.VEHICLE.VIN,
				CDS.VEHICLE.UNITS,
				CDS.DRIVING.ODOMETER,
				CDS.DRIVING.AVERAGECONSUMPTION,
				CDS.DRIVING.AVERAGESPEED,
				CDS.DRIVING.DISPLAYRANGEELECTRICVEHICLE,        // doesn't need unit conversion
				CDS.DRIVING.DRIVINGSTYLE,
				CDS.DRIVING.ECORANGEWON,
				CDS.ENGINE.RANGECALC,
				CDS.NAVIGATION.GPSPOSITION,
				CDS.NAVIGATION.CURRENTPOSITIONDETAILEDINFO,
				CDS.NAVIGATION.GPSEXTENDEDINFO,
				CDS.SENSORS.BATTERY,
				CDS.SENSORS.FUEL,
				CDS.SENSORS.SOCBATTERYHYBRID,
				CDS.VEHICLE.TIME,
				CDS.ENGINE.TEMPERATURE,
				CDS.CONTROLS.SUNROOF,
				CDS.CONTROLS.WINDOWDRIVERFRONT,
				CDS.CONTROLS.WINDOWPASSENGERFRONT,
				CDS.CONTROLS.WINDOWDRIVERREAR,
				CDS.CONTROLS.WINDOWPASSENGERREAR,
				CDS.DRIVING.PARKINGBRAKE,
		)
	}

	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			val handler = Handler()
			var model: CarDrivingStatsModel? = null
			val carInfo = CarInformationObserver {
				handler.post { model?.update() }
			}
			model = CarDrivingStatsModel(carInfo, BooleanLiveSetting(appContext, AppSettings.KEYS.SHOW_ADVANCED_SETTINGS))
			model.update()
			return model as T
		}
	}

	private val carInfo = carInfoOverride ?: CarInformation()

	private val _idriveVersion = MutableLiveData<String>(null)
	val idriveVersion: LiveData<String> = _idriveVersion

	// unit conversions
	val units: LiveData<CDSVehicleUnits> = carInfo.cachedCdsData.liveData[CDS.VEHICLE.UNITS].map(CDSVehicleUnits.UNKNOWN) {
		CDSVehicleUnits.fromCdsProperty(it)
	}

	val unitsAverageConsumptionLabel: LiveData<Context.() -> String> = carInfo.cachedCdsData.liveData[CDS.DRIVING.AVERAGECONSUMPTION].map({getString(R.string.lbl_carinfo_units_L100km)}) {
		val unit = CDSVehicleUnits.Consumption.fromValue(it.tryAsJsonObject("averageConsumption")?.getAsJsonPrimitive("unit")?.tryAsInt)
		when(unit) {
			CDSVehicleUnits.Consumption.MPG_UK -> {{ getString(R.string.lbl_carinfo_units_mpg) }}
			CDSVehicleUnits.Consumption.MPG_US -> {{ getString(R.string.lbl_carinfo_units_mpg) }}
			CDSVehicleUnits.Consumption.KM_L -> {{ getString(R.string.lbl_carinfo_units_kmL) }}
			CDSVehicleUnits.Consumption.L_100km -> {{ getString(R.string.lbl_carinfo_units_L100km) }}
		}
	}

	val unitsAverageSpeedLabel: LiveData<Context.() -> String> = carInfo.cachedCdsData.liveData[CDS.DRIVING.AVERAGESPEED].map({getString(R.string.lbl_carinfo_units_L100km)}) {
		val unit = CDSVehicleUnits.Speed.fromValue(it.tryAsJsonObject("averageSpeed")?.getAsJsonPrimitive("unit")?.tryAsInt)
		when(unit) {
			CDSVehicleUnits.Speed.KMPH -> {{ getString(R.string.lbl_carinfo_units_kmph) }}
			CDSVehicleUnits.Speed.MPH -> {{ getString(R.string.lbl_carinfo_units_mph) }}
		}
	}

	val unitsTemperatureLabel: LiveData<Context.() -> String> = units.map({getString(R.string.lbl_carinfo_units_celcius)}) {
		when (it.temperatureUnits) {
			CDSVehicleUnits.Temperature.CELCIUS -> {{ getString(R.string.lbl_carinfo_units_celcius) }}
			CDSVehicleUnits.Temperature.FAHRENHEIT -> {{ getString(R.string.lbl_carinfo_units_fahrenheit) }}
		}
	}

	val unitsDistanceLabel: LiveData<Context.() -> String> = units.map({getString(R.string.lbl_carinfo_units_km)}) {
		when (it.distanceUnits) {
			CDSVehicleUnits.Distance.Kilometers -> {{ getString(R.string.lbl_carinfo_units_km) }}
			CDSVehicleUnits.Distance.Miles -> {{ getString(R.string.lbl_carinfo_units_mi) }}
		}
	}

	val unitsFuelLabel: LiveData<Context.() -> String> = units.map({getString(R.string.lbl_carinfo_units_liter)}) {
		when (it.fuelUnits) {
			CDSVehicleUnits.Fuel.Liters -> {{ getString(R.string.lbl_carinfo_units_liter) }}
			CDSVehicleUnits.Fuel.Gallons_UK -> {{ getString(R.string.lbl_carinfo_units_gal_uk) }}
			CDSVehicleUnits.Fuel.Gallons_US -> {{ getString(R.string.lbl_carinfo_units_gal_us) }}
		}
	}

	// the visible LiveData objects
	val vin = carInfo.cachedCdsData.liveData[CDS.VEHICLE.VIN].map {
		it.tryAsJsonPrimitive("VIN")?.tryAsString
	}

	val hasConnected = vin.map(false) { true }

	val odometer = carInfo.cachedCdsData.liveData[CDS.DRIVING.ODOMETER].map {
		it.tryAsJsonPrimitive("odometer")?.tryAsDouble
	}.combine(units) { value, units ->
		units.distanceUnits.fromCarUnit(value)
	}.format("%.0f").addUnit(unitsDistanceLabel)

	val lastUpdate = carInfo.cachedCdsData.liveData[CDS.VEHICLE.TIME].map {
		try {
			val carTime = it.getAsJsonObject("time")
			val dateTime = GregorianCalendar(
				carTime.getAsJsonPrimitive("year").asInt,
				carTime.getAsJsonPrimitive("month").asInt - 1,
				carTime.getAsJsonPrimitive("date").asInt,
				carTime.getAsJsonPrimitive("hour").asInt,
				carTime.getAsJsonPrimitive("minute").asInt,
				carTime.getAsJsonPrimitive("second").asInt,
			).time
			DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(dateTime)
		} catch (e: Exception) { null }
	}

	val positionName = carInfo.cachedCdsData.liveData[CDS.NAVIGATION.CURRENTPOSITIONDETAILEDINFO].map {
		val num = it.tryAsJsonObject("currentPositionDetailedInfo")?.tryAsJsonPrimitive("houseNumber")?.tryAsString ?: ""
		val street = it.tryAsJsonObject("currentPositionDetailedInfo")?.tryAsJsonPrimitive("street")?.tryAsString ?: ""
		val crossStreet = it.tryAsJsonObject("currentPositionDetailedInfo")?.tryAsJsonPrimitive("crossStreet")?.tryAsString ?: ""
		val city = it.tryAsJsonObject("currentPositionDetailedInfo")?.tryAsJsonPrimitive("city")?.tryAsString ?: ""
		if (num.isNotBlank() && street.isNotBlank() && city.isNotBlank()) {
			"$num $street, $city"
		} else if (street.isNotBlank() && crossStreet.isNotBlank() && city.isNotBlank()) {
			"$street & $crossStreet, $city"
		} else if (street.isNotBlank() && crossStreet.isNotBlank() && city.isBlank()) {
			"$street & $crossStreet"
		} else if (num.isBlank() && street.isNotBlank() && city.isNotBlank()) {
			"$street, $city"
		} else {
			city
		}
	}
	val positionGeoName = carInfo.cachedCdsData.liveData[CDS.NAVIGATION.GPSPOSITION].map {
		val lat = it.tryAsJsonObject("GPSPosition")?.tryAsJsonPrimitive("latitude")?.tryAsDouble
		val long = it.tryAsJsonObject("GPSPosition")?.tryAsJsonPrimitive("longitude")?.tryAsDouble
		if (lat != null && long != null) {
			"$lat,$long"
		} else {
			null
		}
	}
	val positionGeoUri = carInfo.cachedCdsData.liveData[CDS.NAVIGATION.GPSPOSITION].combine(positionName) { it, name ->
		val lat = it.tryAsJsonObject("GPSPosition")?.tryAsJsonPrimitive("latitude")?.tryAsDouble
		val long = it.tryAsJsonObject("GPSPosition")?.tryAsJsonPrimitive("longitude")?.tryAsDouble
		if (lat != null && long != null) {
			val quotedName = Uri.encode(name)
			Uri.parse("geo:$lat,$long?q=$lat,$long($quotedName)")
		} else {
			null
		}
	}

	val evLevel = carInfo.cachedCdsData.liveData[CDS.SENSORS.SOCBATTERYHYBRID].map {
		it.tryAsJsonPrimitive("SOCBatteryHybrid")?.tryAsDouble?.takeIf { it < 255 }
	}
	val evLevelLabel = evLevel.format("%.1f %%")

	val fuelLevel = carInfo.cachedCdsData.liveData[CDS.SENSORS.FUEL].map {
		it.tryAsJsonObject("fuel")?.tryAsJsonPrimitive("tanklevel")?.tryAsDouble?.takeIf { it > 0 }
	}.combine(units) { value, units ->
		units.fuelUnits.fromCarUnit(value)
	}
	val fuelLevelLabel = fuelLevel.format("%.1f").addUnit(unitsFuelLabel)

	val accBatteryLevel = carInfo.cachedCdsData.liveData[CDS.SENSORS.BATTERY].map {
		it.tryAsJsonPrimitive("battery")?.tryAsDouble?.takeIf { it < 255 }
	}
	val accBatteryLevelLabel = accBatteryLevel.format("%.0f %%")

	val evRange = carInfo.cachedCdsData.liveData[CDS.DRIVING.DISPLAYRANGEELECTRICVEHICLE].map {
		it.tryAsJsonPrimitive("displayRangeElectricVehicle")?.tryAsDouble?.takeIf { it < 4093 }
	}
	val evRangeLabel = evRange.format("%.0f").addUnit(unitsDistanceLabel)

	// a non-nullable evRange for calculating the gas-only fuelRange
	private val _evRange = carInfo.cachedCdsData.liveData[CDS.DRIVING.DISPLAYRANGEELECTRICVEHICLE].map(0.0) {
		it.tryAsJsonPrimitive("displayRangeElectricVehicle")?.tryAsDouble?.takeIf { it < 4093 } ?: 0.0
	}

	val fuelRange = carInfo.cachedCdsData.liveData[CDS.SENSORS.FUEL].map {
		it.tryAsJsonObject("fuel")?.tryAsJsonPrimitive("range")?.tryAsDouble
	}.combine(units) { value, units ->
		units.distanceUnits.fromCarUnit(value)
	}.combine(_evRange) { totalRange, evRange ->
		max(0.0, totalRange - evRange)
	}
	val fuelRangeLabel = fuelRange.format("%.0f").addUnit(unitsDistanceLabel)

	val totalRange = carInfo.cachedCdsData.liveData[CDS.SENSORS.FUEL].map {
		it.tryAsJsonObject("fuel")?.tryAsJsonPrimitive("range")?.tryAsDouble
	}.combine(units) { value, units ->
		units.distanceUnits.fromCarUnit(value)
	}
	val totalRangeLabel = totalRange.format("%.0f").addUnit(unitsDistanceLabel)

	val averageConsumption = carInfo.cachedCdsData.liveData[CDS.DRIVING.AVERAGECONSUMPTION].map {
		it.tryAsJsonObject("averageConsumption")?.tryAsJsonPrimitive("averageConsumption1")?.tryAsDouble
	}.format("%.1f").addUnit(unitsAverageConsumptionLabel)

	val averageSpeed = carInfo.cachedCdsData.liveData[CDS.DRIVING.AVERAGESPEED].map {
		it.tryAsJsonObject("averageSpeed")?.tryAsJsonPrimitive("averageSpeed1")?.tryAsDouble
	}.format("%.1f").addUnit(unitsAverageSpeedLabel)

	val averageConsumption2 = carInfo.cachedCdsData.liveData[CDS.DRIVING.AVERAGECONSUMPTION].map {
		it.tryAsJsonObject("averageConsumption")?.tryAsJsonPrimitive("averageConsumption2")?.tryAsDouble?.takeIf { it < 2093 }
	}.format("%.1f").addUnit(unitsAverageConsumptionLabel)

	val averageSpeed2 = carInfo.cachedCdsData.liveData[CDS.DRIVING.AVERAGESPEED].map {
		it.tryAsJsonObject("averageSpeed")?.tryAsJsonPrimitive("averageSpeed2")?.tryAsDouble?.takeIf { it < 2093 }
	}.format("%.1f").addUnit(unitsAverageSpeedLabel)

	val drivingStyleAccel = carInfo.cachedCdsData.liveData[CDS.DRIVING.DRIVINGSTYLE].map {
		it.tryAsJsonObject("drivingStyle")?.tryAsJsonPrimitive("accelerate")?.tryAsInt
	}
	val drivingStyleBrake = carInfo.cachedCdsData.liveData[CDS.DRIVING.DRIVINGSTYLE].map {
		it.tryAsJsonObject("drivingStyle")?.tryAsJsonPrimitive("brake")?.tryAsInt
	}
	val drivingStyleShift = carInfo.cachedCdsData.liveData[CDS.DRIVING.DRIVINGSTYLE].map {
		it.tryAsJsonObject("drivingStyle")?.tryAsJsonPrimitive("shift")?.tryAsInt
	}

	val ecoRangeWon = carInfo.cachedCdsData.liveData[CDS.DRIVING.ECORANGEWON].map {
		it.tryAsJsonPrimitive("ecoRangeWon")?.tryAsDouble
	}.combine(units) { value, units ->
		units.distanceUnits.fromCarUnit(value)
	}.format("%.1f").addUnit(unitsDistanceLabel)

	fun update() {
		// any other updates
		_idriveVersion.value = carInfo.capabilities["hmi.version"]
	}

	/* JEZIKK additions */
	val engineTemp = carInfo.cachedCdsData.liveData[CDS.ENGINE.TEMPERATURE].map {
		it.tryAsJsonObject("temperature")?.tryAsJsonPrimitive("engine")?.tryAsDouble
	}.format("%.0f").addUnit(unitsTemperatureLabel)
	val oilTemp = carInfo.cachedCdsData.liveData[CDS.ENGINE.TEMPERATURE].map {
		it.tryAsJsonObject("temperature")?.tryAsJsonPrimitive("oil")?.tryAsDouble
	}.format("%.0f").addUnit(unitsTemperatureLabel)
	val speedActual = carInfo.cdsData.liveData[CDS.DRIVING.SPEEDACTUAL].map {
		it.tryAsJsonPrimitive("speedActual")?.tryAsDouble
	}.format("%.0f") //.addUnit(unitsAverageSpeedLabel)
	val speedDisplayed = carInfo.cdsData.liveData[CDS.DRIVING.SPEEDDISPLAYED].map {
		it.tryAsJsonPrimitive("speedDisplayed")?.tryAsDouble
	}.format("%.0f").addUnit(unitsAverageSpeedLabel)
	val tempInterior = carInfo.cdsData.liveData[CDS.SENSORS.TEMPERATUREINTERIOR].map {
		it.tryAsJsonPrimitive("temperatureInterior")?.tryAsDouble
	}.format("%.1f").addUnit(unitsTemperatureLabel)
	val tempExterior = carInfo.cdsData.liveData[CDS.SENSORS.TEMPERATUREEXTERIOR].map {
		it.tryAsJsonPrimitive("temperatureExterior")?.tryAsDouble
	}.format("%.1f").addUnit(unitsTemperatureLabel)

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
	//val drivingMode = carInfo.cdsData.liveData[CDS.DRIVING.MODE].map {
	val drivingMode = carInfo.cdsData.liveData[CDS.DRIVING.MODE].map {
		val a = it.tryAsJsonPrimitive("mode")?.tryAsInt
		if (a != null) {
			when (a) {
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
		else {
			""
		}
	}
	/*
		2 - handbrake
		0 - Bremsensymbol_AUS -> Brake symbol off
		1 - Bremsensymbol_Gelb -> yellow
		2 - Bremsensymbol_Rot -> red
		3 - Bremsemsymbol_Gruen -> green
		4 - PARK_Gelb
		8 - PARK_Rot
		12 - Park_Gruen
		16 - AutoP_Gelb
		32 - AutoP_Rot
		48 - AutoP_Gruen
		127 - Unknown
	 */

	val parkingBrake = carInfo.cachedCdsData.liveData[CDS.DRIVING.PARKINGBRAKE].map {
		val pB = it.tryAsJsonPrimitive("parkingBrake")?.tryAsInt
		if (pB == 2) {
			"HandBrake ON"
		}
		else if (pB == 8 || pB == 32) {
			"AutoPark Brake ON"
		}
		else
			"-$pB-"
	}

	/*
		status: 0 - closed or tilted
		status: 1 - partially open  (not tilted)
		status: 2 - fully open
		tilt: 1->12 -> tilted (tilted degree)
		open: 1-50 -> how far is open

	 */
	val sunroofSupported = carInfo.cachedCdsData.liveData[CDS.CONTROLS.SUNROOF].map(false) {
		val status = it.tryAsJsonObject("sunroof")?.tryAsJsonPrimitive("status")?.tryAsInt
		status == 0 || status == 1 || status == 2      // 3 and null would be false
	}
	val sunRoof = carInfo.cachedCdsData.liveData[CDS.CONTROLS.SUNROOF].map({""}) {
		val status = it.tryAsJsonObject("sunroof")?.tryAsJsonPrimitive("status")?.tryAsInt ?: 0
		val openPosition = it.tryAsJsonObject("sunroof")?.tryAsJsonPrimitive("openPosition")?.tryAsInt ?: 0
		val tiltPosition = it.tryAsJsonObject("sunroof")?.tryAsJsonPrimitive("tiltPosition")?.tryAsInt ?: 0
		val sunRoofString: Context.() -> String = if (status == 0 && tiltPosition == 0 && openPosition == 0) {
			{ getString(R.string.lbl_carinfo_sunroof_closed) }
		} else if (tiltPosition>0 && openPosition == 0) {
			{ getString(R.string.lbl_carinfo_sunroof_tilted) }
		} else if (status == 1 && openPosition >0) {
			{ getString(R.string.lbl_carinfo_sunroof_partial, openPosition * 2) }
		} else if (status == 2) {
			{ getString(R.string.lbl_carinfo_sunroof_open) }
		} else {
			{ "" }
		}
		sunRoofString
		//"DEBUG: Status: $status | Open: $openPosition | Tilt: $tiltPosition"
	}

	private fun parseWindowOpen(data: JsonObject?): Boolean {
		val status = data?.tryAsJsonPrimitive("status")?.tryAsInt ?: 0
		return status == 1 || status == 2
	}
	private fun parseWindowStatus(nameString: Int, data: JsonObject?): Context.() -> String {
		val status = data?.tryAsJsonPrimitive("status")?.tryAsInt ?: 0
		val position = data?.tryAsJsonPrimitive("position")?.tryAsInt ?: 0
		return if (status == 0) {
			{ getString(nameString) + ": " + getString(R.string.lbl_carinfo_window_closed) }
		} else if (status == 1) {
			{ getString(nameString) + ": " + getString(R.string.lbl_carinfo_window_partial, position * 2)}
		} else {
			{ getString(nameString) + ": " + getString(R.string.lbl_carinfo_window_open) }
		}
	}
	val windowDriverFrontOpen = carInfo.cachedCdsData.liveData[CDS.CONTROLS.WINDOWDRIVERFRONT].map(false) {
		parseWindowOpen(it.tryAsJsonObject("windowDriverFront"))
	}
	val windowDriverFrontState = carInfo.cachedCdsData.liveData[CDS.CONTROLS.WINDOWDRIVERFRONT].map({""}) {
		parseWindowStatus(R.string.lbl_carinfo_window_driverfront, it.tryAsJsonObject("windowDriverFront"))
	}
	val windowPassengerFrontOpen = carInfo.cachedCdsData.liveData[CDS.CONTROLS.WINDOWPASSENGERFRONT].map(false) {
		parseWindowOpen(it.tryAsJsonObject("windowPassengerFront"))
	}
	val windowPassengerFrontState = carInfo.cachedCdsData.liveData[CDS.CONTROLS.WINDOWPASSENGERFRONT].map({""}) {
		parseWindowStatus(R.string.lbl_carinfo_window_passengerfront, it.tryAsJsonObject("windowPassengerFront"))
	}
	val windowDriverRearOpen = carInfo.cachedCdsData.liveData[CDS.CONTROLS.WINDOWDRIVERREAR].map(false) {
		parseWindowOpen(it.tryAsJsonObject("windowDriverRear"))
	}
	val windowDriverRearState = carInfo.cachedCdsData.liveData[CDS.CONTROLS.WINDOWDRIVERREAR].map({""}) {
		parseWindowStatus(R.string.lbl_carinfo_window_driverrear, it.tryAsJsonObject("windowDriverRear"))
	}
	val windowPassengerRearOpen = carInfo.cachedCdsData.liveData[CDS.CONTROLS.WINDOWPASSENGERREAR].map(false) {
		parseWindowOpen(it.tryAsJsonObject("windowPassengerRear"))
	}
	val windowPassengerRearState = carInfo.cachedCdsData.liveData[CDS.CONTROLS.WINDOWPASSENGERREAR].map({""}) {
		parseWindowStatus(R.string.lbl_carinfo_window_passengerrear, it.tryAsJsonObject("windowPassengerRear"))
	}
	val windowsAnyOpen = windowDriverFrontOpen.combine(windowPassengerFrontOpen) { acc, i ->
		acc || i
	}.combine(windowDriverRearOpen) { acc, i ->
		acc || i
	}.combine(windowPassengerRearOpen) { acc, i ->
		acc || i
	}

	val drivingGear = carInfo.cdsData.liveData[CDS.DRIVING.GEAR].map {
		var gear = it.tryAsJsonPrimitive("gear")?.tryAsInt?.takeIf { it >0 } ?: 255
		if (gear == 1) {
			"N"
		}
		else if(gear == 2 ) {
			"R"
		}
		else if(gear == 3) {
			"P"
		}
		else if(gear >= 5 ) {
			gear -= 4
			"D $gear"
		}
		else {
			"-"
		}
	}

	val altitude = carInfo.cachedCdsData.liveData[CDS.NAVIGATION.GPSEXTENDEDINFO].map {
		it.tryAsJsonObject("GPSExtendedInfo")?.tryAsJsonPrimitive("altitude")?.tryAsInt
	}

	val headingGPS = carInfo.cachedCdsData.liveData[CDS.NAVIGATION.GPSEXTENDEDINFO].map {
		var heading = it.tryAsJsonObject("GPSExtendedInfo")?.tryAsJsonPrimitive("heading")?.tryAsDouble
		var direction = ""
		if (heading != null) {
			// heading defined in CCW manner, so we ned to invert to CW neutral direction wheel.
			heading *= -1
			heading += 360
			//heading = -100 + 360  = 260;

			if((heading>=0 && heading<22.5) || (heading>=347.5 && heading<=359.99) ) {
				direction = "N"
			}
			else if (heading >= 22.5 && heading < 67.5) {
				direction = "NNE"
			}
			else if (heading>=67.5 && heading<112.5) {
				direction = "E"
			}
			else if (heading>=112.5 && heading < 157.5) {
				direction = "SE"
			}
			else if (heading>=157.5 && heading<202.5) {
				direction = "S"
			}
			else if (heading>=202.5 && heading<247.5) {
				direction = "SW"
			}
			else if (heading>=247.5 && heading<302.5){
				direction = "W"
			}
			else if (heading>=302.5 && heading<347.5) {
				direction = "S"
			}
			else {
				direction = "-"
			}
			round(heading)
		}
		Pair(direction, "$heading")
	}
}