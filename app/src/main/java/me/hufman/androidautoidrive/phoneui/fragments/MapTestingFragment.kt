package me.hufman.androidautoidrive.phoneui.fragments

import android.graphics.PixelFormat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AutoCompleteTextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.maps.MapInteractionControllerIntent
import me.hufman.androidautoidrive.databinding.MapTestingBinding
import me.hufman.androidautoidrive.maps.PlaceSearchProvider
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundArrayAdapter
import me.hufman.androidautoidrive.phoneui.controllers.MapTestingController
import me.hufman.androidautoidrive.phoneui.controllers.NavSearchFilter
import me.hufman.androidautoidrive.phoneui.viewmodels.MapResultViewModel
import me.hufman.androidautoidrive.phoneui.viewmodels.MapTestingModel
import me.hufman.androidautoidrive.phoneui.viewmodels.viewModels

class MapTestingFragment: Fragment() {
	private val viewModel by viewModels<MapTestingModel>()
	val placeSearch by lazy { PlaceSearchProvider(requireContext()).getInstance() }
	val locationAutocompleteFilter by lazy { NavSearchFilter(placeSearch, viewModel.locationAutocompleteResults) }
	val locationAutocompleteAdapter by lazy {
		DataBoundArrayAdapter(requireContext(), R.layout.navigation_listitem, viewModel.locationAutocompleteResults, null, locationAutocompleteFilter)
	}
	val navigationAutocompleteFilter by lazy { NavSearchFilter(placeSearch, viewModel.navigationAutocompleteResults) }
	val navigationAutocompleteAdapter by lazy {
		DataBoundArrayAdapter(requireContext(), R.layout.navigation_listitem, viewModel.navigationAutocompleteResults, null, navigationAutocompleteFilter)
	}
	private val controller by lazy { MapTestingController(
		lifecycleScope,
		viewModel,
		MapInteractionControllerIntent(this.requireContext()),
		CarInformation.cdsData,
		placeSearch,
	) }


	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val binding = MapTestingBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.controller = controller
		binding.viewModel = viewModel

		// autocomplete behaviors
		val autocompleteLocation = binding.root.findViewById<AutoCompleteTextView>(R.id.txtLocationQuery)
		autocompleteLocation.setAdapter(locationAutocompleteAdapter)
		autocompleteLocation.onItemClickListener = AdapterView.OnItemClickListener { adapterView, _, index, _ ->
			val item = adapterView.getItemAtPosition(index) as? MapResultViewModel
			item?.also { controller.setLocation(it.result) }
		}
		val autocompleteNavigation = binding.root.findViewById<AutoCompleteTextView>(R.id.txtNavigationQuery)
		autocompleteNavigation.setAdapter(navigationAutocompleteAdapter)
		autocompleteNavigation.onItemClickListener = AdapterView.OnItemClickListener { adapterView, _, index, _ ->
			val item = adapterView.getItemAtPosition(index) as? MapResultViewModel
			item?.also { controller.startNavigation(it.result) }
		}

		// when NavController updates the query with the full result address
		// it triggers the autocomplete popup, so close it
		viewModel.isSearchingLocation.observe(viewLifecycleOwner) {
			autocompleteLocation.dismissDropDown()
		}
		viewModel.isSearchingNavigation.observe(viewLifecycleOwner) {
			autocompleteNavigation.dismissDropDown()
		}

		return binding.root
	}

	override fun onResume() {
		super.onResume()

		val surfaceView = requireView().findViewById<SurfaceView>(R.id.imgMapTest)
		surfaceView.holder.setFormat(PixelFormat.RGBA_8888)
		surfaceView.holder.addCallback(object: SurfaceHolder.Callback {
			override fun surfaceCreated(p0: SurfaceHolder) {
				controller.start(requireContext(), p0.surface, surfaceView.width, surfaceView.height)
			}

			override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
				controller.start(requireContext(), p0.surface, surfaceView.width, surfaceView.height)
			}

			override fun surfaceDestroyed(p0: SurfaceHolder) {
				controller.pause(requireContext())
			}
		})

		val surface = surfaceView.holder.surface
		if (surface.isValid) {
			controller.start(requireContext(), surface, surfaceView.width, surfaceView.height)
		}
	}
}