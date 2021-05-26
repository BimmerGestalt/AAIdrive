package me.hufman.androidautoidrive.phoneui.viewmodels

import me.hufman.androidautoidrive.carapp.CDSVehicleUnits
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsDouble
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonObject
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonPrimitive
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsString

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.liveData
import me.hufman.androidautoidrive.phoneui.LiveDateHelpers.addUnit
import me.hufman.androidautoidrive.phoneui.LiveDateHelpers.combine
import me.hufman.androidautoidrive.phoneui.LiveDateHelpers.format
import me.hufman.androidautoidrive.phoneui.LiveDateHelpers.map
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsInt
import me.hufman.idriveconnectionkit.CDS
import java.lang.Exception
import java.text.DateFormat
import java.util.*
import kotlin.math.max

class CarDrivingStatsModel(carInfoOverride: CarInformation? = null): ViewModel() {
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
				CDS.SENSORS.FUEL,
				CDS.SENSORS.SOCBATTERYHYBRID,
				CDS.VEHICLE.TIME,
				CDS.SENSORS.BATTERY,
				CDS.ENGINE.TEMPERATURE,
				CDS.NAVIGATION.GPSPOSITION,
				CDS.NAVIGATION.CURRENTPOSITIONDETAILEDINFO

		)
	}

	private val carInfo = carInfoOverride ?: CarInformation()

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

	/*
	val unitsTemperatureLabel: LiveData<Context.() -> String> = units.map({getString(R.string.lbl_carinfo_units_temp)}) {
		when (it.distanceUnits) {
			CDSVehicleUnits.Temperature.Fahrenheit -> {{ getString(R.string.lbl_carinfo_units_f) }}
			CDSVehicleUnits.Temperature.Celcius  -> {{ getString(R.string.lbl_carinfo_units_c) }}
		}
	}
	*/

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

	val evRange = carInfo.cachedCdsData.liveData[CDS.DRIVING.DISPLAYRANGEELECTRICVEHICLE].map {
		it.tryAsJsonPrimitive("displayRangeElectricVehicle")?.tryAsDouble?.takeIf { it < 4095 }
	}
	val evRangeLabel = evRange.format("%.0f").addUnit(unitsDistanceLabel)

	// a non-nullable evRange for calculating the gas-only fuelRange
	private val _evRange = carInfo.cachedCdsData.liveData[CDS.DRIVING.DISPLAYRANGEELECTRICVEHICLE].map(0.0) {
		it.tryAsJsonPrimitive("displayRangeElectricVehicle")?.tryAsDouble?.takeIf { it < 4095 }
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
		it.tryAsJsonObject("averageConsumption")?.tryAsJsonPrimitive("averageConsumption2")?.tryAsDouble
	}.format("%.1f").addUnit(unitsAverageConsumptionLabel)

	val averageSpeed2 = carInfo.cachedCdsData.liveData[CDS.DRIVING.AVERAGESPEED].map {
		it.tryAsJsonObject("averageSpeed")?.tryAsJsonPrimitive("averageSpeed2")?.tryAsDouble
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

	/* JEZIKK additions */
	val carBattery = carInfo.cachedCdsData.liveData[CDS.SENSORS.BATTERY].map {
		it.tryAsJsonPrimitive("battery")?.tryAsDouble
	}.format("%.0f")
	val engineTemp = carInfo.cachedCdsData.liveData[CDS.ENGINE.TEMPERATURE].map {
		it.tryAsJsonObject("temperature")?.tryAsJsonPrimitive("engine")?.tryAsDouble
	}.format("%.0f") //.addUnit(unitsAverageSpeedLabel)
	val oilTemp = carInfo.cachedCdsData.liveData[CDS.ENGINE.TEMPERATURE].map {
		it.tryAsJsonObject("temperature")?.tryAsJsonPrimitive("oil")?.tryAsDouble
	}.format("%.0f") //.addUnit(unitsAverageSpeedLabel)
	val speedActual = carInfo.cdsData.liveData[CDS.DRIVING.SPEEDACTUAL].map {
		it.tryAsJsonPrimitive("speedActual")?.tryAsDouble
	}.format("%.0f").addUnit(unitsAverageSpeedLabel)
	val speedDisplayed = carInfo.cdsData.liveData[CDS.DRIVING.SPEEDDISPLAYED].map {
		it.tryAsJsonPrimitive("speedDisplayed")?.tryAsDouble
	}.format("%.0f").addUnit(unitsAverageSpeedLabel)

	val GPSPosition = carInfo.cachedCdsData.liveData[CDS.NAVIGATION.GPSPOSITION].map {
		val GPSPos = it.getAsJsonObject("GPSPosition")
		val Latitude = GPSPos.getAsJsonPrimitive("latitude")?.tryAsString
		val Longitude = GPSPos.getAsJsonPrimitive("longitude")?.tryAsString
		//"Latitude: "+ Latitude + " Longitude: " + Longitude
		Pair(
		"Latitude: "+ Latitude + " Longitude: " + Longitude,
		Latitude + "," + Longitude
		);
	}

	val GPSLocation = carInfo.cachedCdsData.liveData[CDS.NAVIGATION.CURRENTPOSITIONDETAILEDINFO].map {
		val Location = it.getAsJsonObject("currentPositionDetailedInfo")
		val street = Location.getAsJsonPrimitive("street")?.tryAsString
		val houseNumber = Location.getAsJsonPrimitive("houseNumber")?.tryAsString
		val city = Location.getAsJsonPrimitive("city")?.tryAsString
		val country = Location.getAsJsonPrimitive("country")?.tryAsString
		street + " " + houseNumber + ", " + city + " " + country
	}

	val tempInterior = carInfo.cdsData.liveData[CDS.SENSORS.TEMPERATUREINTERIOR].map {
		it.tryAsJsonPrimitive("temperatureInterior")?.tryAsDouble
	}.format("%.1f") //.addUnit(unitsAverageSpeedLabel)
	val tempExterior = carInfo.cdsData.liveData[CDS.SENSORS.TEMPERATUREEXTERIOR].map {
		it.tryAsJsonPrimitive("temperatureExterior")?.tryAsDouble
	}.format("%.1f") //.addUnit(unitsAverageSpeedLabel)

}