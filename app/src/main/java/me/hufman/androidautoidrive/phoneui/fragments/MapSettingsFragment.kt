package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_map_settings.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.MainActivity
import java.util.*
import kotlin.math.max

class MapSettingsFragment: Fragment() {
	val appSettings by lazy { MutableAppSettingsReceiver(requireContext()) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_map_settings, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		swGmapSyle.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
			override fun onNothingSelected(parent: AdapterView<*>?) {
			}

			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				val value = parent?.getItemAtPosition(position) ?: return
				Log.i(MainActivity.TAG, "Setting gmaps style to $value")
				appSettings[AppSettings.KEYS.GMAPS_STYLE] = value.toString().toLowerCase(Locale.ROOT).replace(' ', '_')
			}
		}
		swMapWidescreen.setOnCheckedChangeListener { _, isChecked ->
			appSettings[AppSettings.KEYS.MAP_WIDESCREEN] = isChecked.toString()
		}
		swMapInvertZoom.setOnCheckedChangeListener { _, isChecked ->
			appSettings[AppSettings.KEYS.MAP_INVERT_SCROLL] = isChecked.toString()
		}
	}

	override fun onResume() {
		super.onResume()

		redraw()
	}

	fun redraw() {
		swMapWidescreen.isChecked = appSettings[AppSettings.KEYS.MAP_WIDESCREEN].toBoolean()
		swMapInvertZoom.isChecked = appSettings[AppSettings.KEYS.MAP_INVERT_SCROLL].toBoolean()

		val gmapStylePosition = resources.getStringArray(R.array.gmaps_styles).map { title ->
			title.toLowerCase(Locale.ROOT).replace(' ', '_')
		}.indexOf(appSettings[AppSettings.KEYS.GMAPS_STYLE].toLowerCase(Locale.ROOT))
		swGmapSyle.setSelection(max(0, gmapStylePosition))
	}
}