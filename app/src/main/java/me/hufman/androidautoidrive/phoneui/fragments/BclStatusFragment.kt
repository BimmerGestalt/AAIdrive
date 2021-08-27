package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.AppSettingsViewer
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.connections.BclStatusListener
import me.hufman.androidautoidrive.phoneui.ViewHelpers.visible

class BclStatusFragment: Fragment() {

	// only show if Advanced Info is showing
	val appSettings = AppSettingsViewer()
	// listen for debug BCL reports
	var bclNextRedraw: Long = 0
	val bclStatusListener by lazy {
		BclStatusListener(requireContext()) {
			if (bclNextRedraw < SystemClock.uptimeMillis()) {
				redraw()
				bclNextRedraw = SystemClock.uptimeMillis() + CarAdvancedInfoFragment.REDRAW_DEBOUNCE
			}
		}
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_bcl_status, container, false)
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
		view?.findViewById<TextView>(R.id.txtBclReport)?.text = bclStatusListener.toString()
		view?.findViewById<ViewGroup>(R.id.paneBclReport)?.visible = bclStatusListener.state != "UNKNOWN" && bclStatusListener.staleness < 30000 && appSettings[AppSettings.KEYS.SHOW_ADVANCED_SETTINGS].toBoolean()
	}
}