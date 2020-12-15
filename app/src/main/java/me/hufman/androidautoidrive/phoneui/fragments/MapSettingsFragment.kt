package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_map_settings.*
import me.hufman.androidautoidrive.*
import java.util.*
import kotlin.math.max

class MapSettingsFragment: Fragment() {
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_map_settings, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val styleSetting = StringLiveSetting(requireContext(), AppSettings.KEYS.GMAPS_STYLE)
		styleSetting.observe(viewLifecycleOwner, androidx.lifecycle.Observer {
			val mapStylePosition = resources.getStringArray(R.array.gmaps_styles).map { title ->
				title.toLowerCase(Locale.ROOT).replace(' ', '_')
			}.indexOf(it.toLowerCase(Locale.ROOT))
			swGmapSyle.setSelection(max(0, mapStylePosition))
		})
		swGmapSyle.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
			override fun onNothingSelected(parent: AdapterView<*>?) {
			}
			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				val value = parent?.getItemAtPosition(position) ?: return
				styleSetting.setValue(value.toString().toLowerCase(Locale.ROOT).replace(' ', '_'))
			}
		}

		val widescreenSetting = BooleanLiveSetting(requireContext(), AppSettings.KEYS.MAP_WIDESCREEN)
		widescreenSetting.observe(viewLifecycleOwner, androidx.lifecycle.Observer {
			swMapWidescreen.isChecked = it
		})
		swMapWidescreen.setOnCheckedChangeListener { _, isChecked ->
			widescreenSetting.setValue(isChecked)
		}

		val invertZoomSetting = BooleanLiveSetting(requireContext(), AppSettings.KEYS.MAP_INVERT_SCROLL)
		invertZoomSetting.observe(viewLifecycleOwner, androidx.lifecycle.Observer {
			swMapInvertZoom.isChecked = it
		})
		swMapInvertZoom.setOnCheckedChangeListener { _, isChecked ->
			widescreenSetting.setValue(isChecked)
		}
	}
}