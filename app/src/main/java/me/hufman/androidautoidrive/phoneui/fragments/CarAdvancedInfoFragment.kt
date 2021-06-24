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
import me.hufman.androidautoidrive.phoneui.ViewHelpers.visible
import me.hufman.idriveconnectionkit.CDS

class CarAdvancedInfoFragment: Fragment() {
	companion object {
		const val REDRAW_DEBOUNCE = 500

		val CDS_KEYS = mapOf(
			CDS.ENGINE.INFO to "info",
			CDS.ENGINE.TEMPERATURE to "temperature",
			CDS.ENGINE.CONSUMPTION to "consumption",
			CDS.DRIVING.MODE to "mode",
			CDS.ENGINE.TORQUE to "torque",
			CDS.ENGINE.RPMSPEED to "RPMSpeed",
			CDS.DRIVING.GEAR to "gear",
			CDS.DRIVING.DRIVINGSTYLE to "drivingStyle",
			CDS.SENSORS.BATTERY to "battery",
			CDS.SENSORS.BATTERYTEMP to "batteryTemp",
			CDS.CONTROLS.HEADLIGHTS to "headlights",
			CDS.CONTROLS.LIGHTS to "lights",
			CDS.CONTROLS.CONVERTIBLETOP to "convertibleTop",
			CDS.CONTROLS.SUNROOF to "sunroof",
			CDS.CONTROLS.WINDOWDRIVERFRONT to "windowDriverFront",
			CDS.CONTROLS.WINDOWPASSENGERFRONT to "windowPassengerFront",
			CDS.CONTROLS.WINDOWDRIVERREAR to "windowDriverRear",
			CDS.CONTROLS.WINDOWPASSENGERREAR to "windowPassengerRear",
			CDS.SENSORS.FUEL to "fuel",
			CDS.DRIVING.DISPLAYRANGEELECTRICVEHICLE to "displayRangeElectricVehicle",
			CDS.ENGINE.ELECTRICVEHICLEMODE to "electricVehicleMode",
			CDS.DRIVING.ELECTRICALPOWERDISTRIBUTION to "electricalPowerDistribution",
			CDS.SENSORS.TEMPERATUREINTERIOR to "temperatureInterior",
			CDS.CLIMATE.AIRCONDITIONERCOMPRESSOR to "ACCompressor",
			CDS.CLIMATE.ACMODE to "ACMode",
			CDS.CLIMATE.ACSYSTEMTEMPERATURES to "ACSystemTemperatures",
			CDS.ENTERTAINMENT.MULTIMEDIA to "multimedia",
			CDS.ENTERTAINMENT.RADIOSTATION to "radioStation",
			CDS.HMI.GRAPHICALCONTEXT to "graphicalContext",
			CDS.NAVIGATION.GPSPOSITION to "GPSPosition",
			CDS.NAVIGATION.GPSEXTENDEDINFO to "GPSExtendedInfo",
			CDS.NAVIGATION.CURRENTPOSITIONDETAILEDINFO to "currentPositionDetailedInfo",
		)
	}

	val carInformationObserver = CarInformationObserver {
		activity?.runOnUiThread { redraw() }
	}
	var cdsNextRedraw: Long = 0
	val redrawCdsObserver = Observer<JsonObject> {
		if (cdsNextRedraw < SystemClock.uptimeMillis()) {
			redrawCds()
			cdsNextRedraw = SystemClock.uptimeMillis() + REDRAW_DEBOUNCE
		}
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_car_advancedinfo, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		CDS_KEYS.keys.forEach {
			carInformationObserver.cdsData.liveData[it].observe(viewLifecycleOwner, redrawCdsObserver)
		}
	}

	override fun onResume() {
		super.onResume()

		redraw()
	}

	fun redraw() {
		if (!isResumed) return
		redrawCds()

		val carCapabilities = carInformationObserver.capabilities.map {
			"${it.key}: ${it.value}"
		}.sorted().joinToString("\n")
		txtCarCapabilities.text = carCapabilities
		paneCarCapabilities.visible = carCapabilities.isNotBlank()
	}

	fun redrawCds() {
		if (!isResumed) return      // CDS Listener calls us directly, check isResumed here too
		val cdsView = CDS_KEYS.map {
			"${it.key.propertyName}: ${carInformationObserver.cdsData[it.key]?.get(it.value)}"
		}.joinToString("\n").trim()
		txtCdsView.text = cdsView
		paneCdsView.visible = cdsView.isNotBlank()
	}
}