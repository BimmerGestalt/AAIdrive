package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.google.gson.JsonObject
import kotlinx.android.synthetic.main.fragment_car_advancedinfo.*
import me.hufman.androidautoidrive.CarInformationObserver
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.liveData
import me.hufman.androidautoidrive.connections.BclStatusListener
import me.hufman.androidautoidrive.phoneui.ViewHelpers.visible
import me.hufman.idriveconnectionkit.CDS

class CarAdvancedInfoFragment: Fragment() {
	companion object {
		const val REDRAW_DEBOUNCE = 100
		/*
		val CDS_KEYS = mapOf(
			CDS.ENGINE.TEMPERATURE to "temperature",
			CDS.ENGINE.TORQUE to "torque",
			CDS.ENGINE.RPMSPEED to "RPMSpeed",
			CDS.DRIVING.GEAR to "gear",
			CDS.SENSORS.BATTERY to "battery",
			CDS.SENSORS.BATTERYTEMP to "batteryTemp",
			CDS.SENSORS.FUEL to "fuel",
			CDS.SENSORS.TEMPERATUREINTERIOR to "temperatureInterior",
			CDS.CLIMATE.AIRCONDITIONERCOMPRESSOR to "ACCompressor",
			CDS.CLIMATE.ACSYSTEMTEMPERATURES to "ACSystemTemperatures",
		)
		*/
		/*   https://github.com/hufman/IDriveConnectAddons#car-data-service   */
		val CDS_KEYS = mapOf(
				// CDS.DRIVING.ODOMETER to "odometer",
				//CDS.SENSORS.FUEL to "fuel",
				//CDS.SENSORS.BATTERY to "battery",
				CDS.NAVIGATION.CURRENTPOSITIONDETAILEDINFO to "currentPositionDetailedInfo",
				CDS.NAVIGATION.GPSPOSITION to "GPSPosition",
				CDS.NAVIGATION.GPSEXTENDEDINFO to "GPSExtendedInfo",
				//CDS.ENGINE.TEMPERATURE to "temperature",
				CDS.DRIVING.PARKINGBRAKE to "ParkingBrake",
				//CDS.DRIVING.AVERAGECONSUMPTION to "averageConsumption",
				/* --- */
				CDS.ENGINE.TORQUE to "torque",
				CDS.ENGINE.RPMSPEED to "RPMSpeed",
				// CDS.ENGINE.RANGECALC to "RangeCalc",
				CDS.DRIVING.GEAR to "gear",
				CDS.DRIVING.ACCELERATION to "acceleration",
				CDS.DRIVING.ACCELERATORPEDAL to "acceleratorPedal",
				// CDS.DRIVING.BRAKECONTACT to "Brake",
				CDS.DRIVING.SPEEDACTUAL to "speedActual",
				CDS.DRIVING.SPEEDDISPLAYED to "speedDisplayed",
				//CDS.DRIVING.DRIVINGSTYLE to "drivingStyle",
				// CDS.SENSORS.BATTERYTEMP to "batteryTemp",
				CDS.SENSORS.TEMPERATUREINTERIOR to "temperatureInterior",
				CDS.SENSORS.TEMPERATUREEXTERIOR to "temperatureExterior",
				// CDS.SENSORS.PDCRANGEFRONT to "PDCRangeFront",
				// CDS.SENSORS.PDCRANGEREAR to "PDCRangeRear",
				// CDS.CLIMATE.AIRCONDITIONERCOMPRESSOR to "ACCompressor",
				CDS.CLIMATE.ACMODE to "ACMode",
				// CDS.CLIMATE.ACCOMPRESSOR to "ACCompressor2",
				CDS.CLIMATE.ACSYSTEMTEMPERATURES to "ACSystemTemperatures",
				// CDS.CONTROLS.LIGHTS to "Lights",
				// CDS.CONTROLS.STARTSTOPSTATUS to "StartStop_Status",
				// CDS.CONTROLS.SUNROOF to "Sunroof",
				CDS.VEHICLE.UNITS to "Units",
		)
	}

	// listen for debug BCL reports
	var bclNextRedraw: Long = 0
	val bclStatusListener by lazy {
		BclStatusListener(requireContext()) {
			if (bclNextRedraw < SystemClock.uptimeMillis()) {
				redraw()
				bclNextRedraw = SystemClock.uptimeMillis() + REDRAW_DEBOUNCE
			}
		}
	}
	val carInformationObserver = CarInformationObserver {
		activity?.runOnUiThread { redraw() }
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_car_advancedinfo, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val redrawCdsObserver = Observer<JsonObject> {
			redrawCds()
		}
		CDS_KEYS.keys.forEach {
			carInformationObserver.cdsData.liveData[it].observe(viewLifecycleOwner, redrawCdsObserver)
		}
	}

	override fun onResume() {
		super.onResume()

		redraw()

		bclStatusListener.subscribe()
	}

	override fun onPause() {
		super.onPause()

		bclStatusListener.unsubscribe()
	}

	fun redraw() {
		if (!isResumed) return
		txtBclReport.text = bclStatusListener.toString()
		paneBclReport.visible = bclStatusListener.state != "UNKNOWN" && bclStatusListener.staleness < 30000

		redrawCds()

		val carCapabilities = carInformationObserver.capabilities.map {
			"${it.key}: ${it.value}"
		}.sorted().joinToString("\n")
		txtCarCapabilities.text = carCapabilities
		paneCarCapabilities.visible = carCapabilities.isNotBlank()
	}

	fun redrawCds() {
		val cdsView = CDS_KEYS.map {
			"${it.key.propertyName}: ${carInformationObserver.cdsData[it.key]?.get(it.value)}"
		}.joinToString("\n").trim()
		txtCdsView.text = cdsView
		paneCdsView.visible = cdsView.isNotBlank()
	}
}