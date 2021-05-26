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
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.liveData
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.addUnit
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.combine
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.format
import me.hufman.androidautoidrive.phoneui.LiveDataHelpers.map
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsInt
import me.hufman.idriveconnectionkit.CDS
import java.lang.Exception
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
				CDS.SENSORS.BATTERY,
				CDS.SENSORS.FUEL,
				CDS.SENSORS.SOCBATTERYHYBRID,
				CDS.VEHICLE.TIME,
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
		it.tryAsJsonPrimitive("displayRangeElectricVehicle")?.tryAsDouble?.takeIf { it < 4095 }
	}
	val evRangeLabel = evRange.format("%.0f").addUnit(unitsDistanceLabel)

	// a non-nullable evRange for calculating the gas-only fuelRange
	private val _evRange = carInfo.cachedCdsData.liveData[CDS.DRIVING.DISPLAYRANGEELECTRICVEHICLE].map(0.0) {
		it.tryAsJsonPrimitive("displayRangeElectricVehicle")?.tryAsDouble?.takeIf { it < 4095 } ?: 0.0
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
}