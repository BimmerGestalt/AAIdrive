package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import kotlinx.android.synthetic.main.fragment_car_advancedinfo.*
import me.hufman.androidautoidrive.CarInformationObserver
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.connections.BclStatusListener
import me.hufman.androidautoidrive.phoneui.visible

class CarAdvancedInfoFragment: Fragment() {
	companion object {
		const val REDRAW_DEBOUNCE = 100
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

		val carCapabilities = carInformationObserver.capabilities.map {
			"${it.key}: ${it.value}"
		}.sorted().joinToString("\n")
		txtCarCapabilities.text = carCapabilities
		paneCarCapabilities.visible = carCapabilities.isNotEmpty()
	}
}