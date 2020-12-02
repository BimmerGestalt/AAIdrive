package me.hufman.androidautoidrive.phoneui.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_car_advancedinfo.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.connections.BclStatusListener
import me.hufman.androidautoidrive.phoneui.DebugStatus
import me.hufman.androidautoidrive.phoneui.SetupActivity
import me.hufman.androidautoidrive.phoneui.visible

class CarAdvancedInfoFragment: Fragment() {
	companion object {
		const val REDRAW_DEBOUNCE = 100
	}

	val redrawListener = object: BroadcastReceiver() {
		override fun onReceive(p0: Context?, p1: Intent?) {
			redraw()
		}
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

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_car_advancedinfo, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
	}

	override fun onResume() {
		super.onResume()

		redraw()

		bclStatusListener.subscribe()
		requireContext().registerReceiver(redrawListener, IntentFilter(SetupActivity.INTENT_REDRAW))
	}

	override fun onPause() {
		super.onPause()

		bclStatusListener.unsubscribe()
		requireContext().unregisterReceiver(redrawListener)
	}

	fun redraw() {
		txtBclReport.text = bclStatusListener.toString()
		paneBclReport.visible = bclStatusListener.state != "UNKNOWN" && bclStatusListener.staleness < 30000

		val carCapabilities = synchronized(DebugStatus.carCapabilities) {
			DebugStatus.carCapabilities.map {
				"${it.key}: ${it.value}"
			}.sorted().joinToString("\n")
		}
		txtCarCapabilities.text = carCapabilities
		paneCarCapabilities.visible = carCapabilities.isNotEmpty()
	}
}