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
import me.hufman.androidautoidrive.phoneui.visible
import me.hufman.idriveconnectionkit.CDS

class CarAdvancedInfoFragment: Fragment() {
	companion object {
		const val REDRAW_DEBOUNCE = 100

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