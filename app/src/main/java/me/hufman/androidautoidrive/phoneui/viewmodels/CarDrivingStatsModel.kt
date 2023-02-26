package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.*
import com.google.gson.JsonObject
import io.bimmergestalt.idriveconnectkit.CDS
import kotlinx.coroutines.flow.*
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.cds.CDSMetrics
import me.hufman.androidautoidrive.cds.CDSVehicleUnits
import me.hufman.androidautoidrive.cds.flow
import me.hufman.androidautoidrive.phoneui.FlowUtils.addContextUnit
import me.hufman.androidautoidrive.phoneui.FlowUtils.format
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.combine
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.map
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsDouble
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsInt
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonObject
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonPrimitive
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsString
import java.lang.Math.round
import java.text.DateFormat
import java.util.*

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
			val handler = Handler(Looper.getMainLooper())
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
	private val cdsMetrics = CDSMetrics(carInfo)

	private val _idriveVersion = MutableLiveData<String>(null)
	val idriveVersion: LiveData<String> = _idriveVersion

	// unit conversions
	val unitsAverageConsumption: Flow<Context.() -> String> = cdsMetrics.unitsAverageConsumption.map { unit ->
		when(unit) {
			CDSVehicleUnits.Consumption.MPG_UK -> {{ getString(R.string.lbl_carinfo_units_mpg) }}
			CDSVehicleUnits.Consumption.MPG_US -> {{ getString(R.string.lbl_carinfo_units_mpg) }}
			CDSVehicleUnits.Consumption.KM_L -> {{ getString(R.string.lbl_carinfo_units_kmL) }}
			CDSVehicleUnits.Consumption.L_100km -> {{ getString(R.string.lbl_carinfo_units_L100km) }}
		}
	}

	val unitsAverageSpeed: Flow<Context.() -> String> = cdsMetrics.unitsAverageSpeed.map { unit ->
		when(unit) {
			CDSVehicleUnits.Speed.KMPH -> {{ getString(R.string.lbl_carinfo_units_kmph) }}
			CDSVehicleUnits.Speed.MPH -> {{ getString(R.string.lbl_carinfo_units_mph) }}
		}
	}
	val unitsAverageSpeedLabel = unitsAverageSpeed.asLiveData()

	val unitsTemperature: Flow<Context.() -> String> = cdsMetrics.units.map {
		when (it.temperatureUnits) {
			CDSVehicleUnits.Temperature.CELCIUS -> {{ getString(R.string.lbl_carinfo_units_celcius) }}
			CDSVehicleUnits.Temperature.FAHRENHEIT -> {{ getString(R.string.lbl_carinfo_units_fahrenheit) }}
		}
	}

	val unitsDistance: Flow<Context.() -> String> = cdsMetrics.units.map {
		when (it.distanceUnits) {
			CDSVehicleUnits.Distance.Kilometers -> {{ getString(R.string.lbl_carinfo_units_km) }}
			CDSVehicleUnits.Distance.Miles -> {{ getString(R.string.lbl_carinfo_units_mi) }}
		}
	}

	val unitsFuel: Flow<Context.() -> String> = cdsMetrics.units.map {
		when (it.fuelUnits) {
			CDSVehicleUnits.Fuel.Liters -> {{ getString(R.string.lbl_carinfo_units_liter) }}
			CDSVehicleUnits.Fuel.Gallons_UK -> {{ getString(R.string.lbl_carinfo_units_gal_uk) }}
			CDSVehicleUnits.Fuel.Gallons_US -> {{ getString(R.string.lbl_carinfo_units_gal_us) }}
		}
	}

	// the visible LiveData objects
	val vin = carInfo.cachedCdsData.flow[CDS.VEHICLE.VIN].mapNotNull {
		it.tryAsJsonPrimitive("VIN")?.tryAsString
	}.asLiveData()

	val hasConnected = vin.map(false) { true }

	val odometer = carInfo.cachedCdsData.flow[CDS.DRIVING.ODOMETER].mapNotNull {
		it.tryAsJsonPrimitive("odometer")?.tryAsDouble
	}.combine(cdsMetrics.units) { value, units ->
		units.distanceUnits.fromCarUnit(value)
	}.format("%.0f").addContextUnit(unitsDistance).asLiveData()

	val lastUpdate = carInfo.cachedCdsData.flow[CDS.VEHICLE.TIME].mapNotNull {
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
	}.asLiveData()

	val positionName = carInfo.cachedCdsData.flow[CDS.NAVIGATION.CURRENTPOSITIONDETAILEDINFO].map {
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
	}.asLiveData()
	val positionGeoName = carInfo.cachedCdsData.flow[CDS.NAVIGATION.GPSPOSITION].mapNotNull {
		val lat = it.tryAsJsonObject("GPSPosition")?.tryAsJsonPrimitive("latitude")?.tryAsDouble
		val long = it.tryAsJsonObject("GPSPosition")?.tryAsJsonPrimitive("longitude")?.tryAsDouble
		if (lat != null && long != null) {
			"$lat,$long"
		} else {
			null
		}
	}.asLiveData()
	val positionGeoUri = positionGeoName.combine(positionName) { latlong, name ->
		val quotedName = Uri.encode(name)
		Uri.parse("geo:$latlong?q=$latlong($quotedName)")
	}
	val altitude = carInfo.cachedCdsData.flow[CDS.NAVIGATION.GPSEXTENDEDINFO].map {
		it.tryAsJsonObject("GPSExtendedInfo")?.tryAsJsonPrimitive("altitude")?.tryAsInt
	}.asLiveData()

	val evLevel = flow {emit(null); emitAll(cdsMetrics.evLevel)}.asLiveData()   // nullable to control visibility
	val evLevelLabel = cdsMetrics.evLevel.format("%.1f%%").asLiveData()
	val fuelLevel = flow {emit(null); emitAll(cdsMetrics.fuelLevel)}.asLiveData()   // nullable to control visibility
	val fuelLevelLabel = cdsMetrics.fuelLevel.format("%.1f").addContextUnit(unitsFuel).asLiveData()

	val accBatteryLevelLabel = cdsMetrics.accBatteryLevel.format("%.0f%%").asLiveData()

	val evRangeLabel = cdsMetrics.evRange.format("%.0f").addContextUnit(unitsDistance).asLiveData()
	val fuelRangeLabel = cdsMetrics.fuelRange.format("%.0f").addContextUnit(unitsDistance).asLiveData()
	val totalRangeLabel = cdsMetrics.totalRange.format("%.0f").addContextUnit(unitsDistance).asLiveData()

	val averageConsumption = flow {emit(null); emitAll(carInfo.cachedCdsData.flow[CDS.DRIVING.AVERAGECONSUMPTION].mapNotNull {
		it.tryAsJsonObject("averageConsumption")?.tryAsJsonPrimitive("averageConsumption1")?.tryAsDouble
	}.format("%.1f").addContextUnit(unitsAverageConsumption))}.asLiveData()

	val averageSpeed = carInfo.cachedCdsData.flow[CDS.DRIVING.AVERAGESPEED].mapNotNull {
		it.tryAsJsonObject("averageSpeed")?.tryAsJsonPrimitive("averageSpeed1")?.tryAsDouble
	}.format("%.1f").addContextUnit(unitsAverageSpeed).asLiveData()

	val averageConsumption2 = carInfo.cachedCdsData.flow[CDS.DRIVING.AVERAGECONSUMPTION].mapNotNull {
		it.tryAsJsonObject("averageConsumption")?.tryAsJsonPrimitive("averageConsumption2")?.tryAsDouble?.takeIf { it < 2093 }
	}.format("%.1f").addContextUnit(unitsAverageConsumption).asLiveData()

	val averageSpeed2 = carInfo.cachedCdsData.flow[CDS.DRIVING.AVERAGESPEED].mapNotNull {
		it.tryAsJsonObject("averageSpeed")?.tryAsJsonPrimitive("averageSpeed2")?.tryAsDouble?.takeIf { it < 2093 }
	}.format("%.1f").addContextUnit(unitsAverageSpeed).asLiveData()

	val drivingStyleAccel = carInfo.cachedCdsData.flow[CDS.DRIVING.DRIVINGSTYLE].mapNotNull {
		it.tryAsJsonObject("drivingStyle")?.tryAsJsonPrimitive("accelerate")?.tryAsInt
	}.asLiveData()
	val drivingStyleBrake = carInfo.cachedCdsData.flow[CDS.DRIVING.DRIVINGSTYLE].mapNotNull {
		it.tryAsJsonObject("drivingStyle")?.tryAsJsonPrimitive("brake")?.tryAsInt
	}.asLiveData()
	val drivingStyleShift = carInfo.cachedCdsData.flow[CDS.DRIVING.DRIVINGSTYLE].mapNotNull {
		it.tryAsJsonObject("drivingStyle")?.tryAsJsonPrimitive("shift")?.tryAsInt
	}.asLiveData()

	val ecoRangeWon = carInfo.cachedCdsData.flow[CDS.DRIVING.ECORANGEWON].mapNotNull {
		it.tryAsJsonPrimitive("ecoRangeWon")?.tryAsDouble
	}.combine(cdsMetrics.units) { value, units ->
		units.distanceUnits.fromCarUnit(value)
	}.format("%.1f").addContextUnit(unitsDistance).asLiveData()

	fun update() {
		// any other updates
		_idriveVersion.value = carInfo.capabilities["hmi.version"]
	}

	/* JEZIKK additions */
	val engineTemp = cdsMetrics.engineTemp.format("%.0f").addContextUnit(unitsTemperature).asLiveData()
	val oilTemp = cdsMetrics.oilTemp.format("%.0f").addContextUnit(unitsTemperature).asLiveData()
	val speedActual = cdsMetrics.speedActual.format("%.0f").asLiveData() //.addUnit(unitsAverageSpeedLabel)
	val speedDisplayed = cdsMetrics.speedDisplayed.format("%.0f").addContextUnit(unitsAverageSpeed).asLiveData()
	val tempInterior = cdsMetrics.tempInterior.format("%.1f").addContextUnit(unitsTemperature).asLiveData()
	val tempExterior = cdsMetrics.tempExterior.format("%.1f").addContextUnit(unitsTemperature).asLiveData()

	val drivingMode = cdsMetrics.drivingMode.asLiveData()
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
	val parkingBrake = carInfo.cachedCdsData.flow[CDS.DRIVING.PARKINGBRAKE].map {
		val pB = it.tryAsJsonPrimitive("parkingBrake")?.tryAsInt
		if (pB == 2) {
			"HandBrake ON"
		}
		else if (pB == 8 || pB == 32) {
			"AutoPark Brake ON"
		}
		else
			"-$pB-"
	}.asLiveData()

	/*
		status: 0 - closed or tilted
		status: 1 - partially open  (not tilted)
		status: 2 - fully open
		tilt: 1->12 -> tilted (tilted degree)
		open: 1-50 -> how far is open

	 */
	val sunroofSupported = flow { emit(false); emitAll(carInfo.cachedCdsData.flow[CDS.CONTROLS.SUNROOF].map {
		val status = it.tryAsJsonObject("sunroof")?.tryAsJsonPrimitive("status")?.tryAsInt
		status == 0 || status == 1 || status == 2      // 3 and null would be false
	})}.asLiveData()    // nullability to control visibility
	val sunRoof = carInfo.cachedCdsData.flow[CDS.CONTROLS.SUNROOF].map {
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
	}.asLiveData()

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
	val windowDriverFrontOpen = carInfo.cachedCdsData.flow[CDS.CONTROLS.WINDOWDRIVERFRONT].map {
		parseWindowOpen(it.tryAsJsonObject("windowDriverFront"))
	}.asLiveData()
	val windowDriverFrontState = carInfo.cachedCdsData.flow[CDS.CONTROLS.WINDOWDRIVERFRONT].map {
		parseWindowStatus(R.string.lbl_carinfo_window_driverfront, it.tryAsJsonObject("windowDriverFront"))
	}.asLiveData()
	val windowPassengerFrontOpen = carInfo.cachedCdsData.flow[CDS.CONTROLS.WINDOWPASSENGERFRONT].map {
		parseWindowOpen(it.tryAsJsonObject("windowPassengerFront"))
	}.asLiveData()
	val windowPassengerFrontState = carInfo.cachedCdsData.flow[CDS.CONTROLS.WINDOWPASSENGERFRONT].map {
		parseWindowStatus(R.string.lbl_carinfo_window_passengerfront, it.tryAsJsonObject("windowPassengerFront"))
	}.asLiveData()
	val windowDriverRearOpen = carInfo.cachedCdsData.flow[CDS.CONTROLS.WINDOWDRIVERREAR].map {
		parseWindowOpen(it.tryAsJsonObject("windowDriverRear"))
	}.asLiveData()
	val windowDriverRearState = carInfo.cachedCdsData.flow[CDS.CONTROLS.WINDOWDRIVERREAR].map {
		parseWindowStatus(R.string.lbl_carinfo_window_driverrear, it.tryAsJsonObject("windowDriverRear"))
	}.asLiveData()
	val windowPassengerRearOpen = carInfo.cachedCdsData.flow[CDS.CONTROLS.WINDOWPASSENGERREAR].map {
		parseWindowOpen(it.tryAsJsonObject("windowPassengerRear"))
	}.asLiveData()
	val windowPassengerRearState = carInfo.cachedCdsData.flow[CDS.CONTROLS.WINDOWPASSENGERREAR].map {
		parseWindowStatus(R.string.lbl_carinfo_window_passengerrear, it.tryAsJsonObject("windowPassengerRear"))
	}.asLiveData()
	val windowsAnyOpen = windowDriverFrontOpen.combine(windowPassengerFrontOpen) { acc, i ->
		acc || i
	}.combine(windowDriverRearOpen) { acc, i ->
		acc || i
	}.combine(windowPassengerRearOpen) { acc, i ->
		acc || i
	}

	val drivingGear = cdsMetrics.drivingGearName.asLiveData()

	val headingGPS = carInfo.cachedCdsData.flow[CDS.NAVIGATION.GPSEXTENDEDINFO].map {
		var heading = it.tryAsJsonObject("GPSExtendedInfo")?.tryAsJsonPrimitive("heading")?.tryAsDouble
		var direction = ""
		if (heading != null) {
			// heading defined in CCW manner, so we ned to invert to CW neutral direction wheel.
			heading *= -1
			heading += 360
			//heading = -100 + 360  = 260;

			if((heading>=0 && heading<22.5) || (heading>=347.5 && heading<=360) ) {
				direction = "N"
			}
			else if (heading >= 22.5 && heading < 67.5) {
				direction = "NE"
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
				direction = "NW"
			}
			else {
				direction = "-"
			}
			round(heading)
		}
		Pair(direction, "$heading")
	}.asLiveData()
}