package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AutoCompleteTextView
import android.widget.Filter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.runBlocking
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.navigation.*
import me.hufman.androidautoidrive.databinding.NavigationStatusBindingImpl
import me.hufman.androidautoidrive.maps.MapPlaceSearch
import me.hufman.androidautoidrive.maps.MapResult
import me.hufman.androidautoidrive.maps.PlaceSearchProvider
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundArrayAdapter
import me.hufman.androidautoidrive.phoneui.controllers.NavigationSearchController
import me.hufman.androidautoidrive.phoneui.viewmodels.NavigationStatusModel
import me.hufman.androidautoidrive.phoneui.viewmodels.viewModels

class NavigationPageFragment: Fragment() {
	val viewModel by viewModels<NavigationStatusModel> { NavigationStatusModel.Factory(requireContext().applicationContext) }
	val placeSearch by lazy { PlaceSearchProvider(requireContext()).getInstance() }
	val filter by lazy { NavSearchFilter(placeSearch, viewModel.autocompleteResults) }
	val adapter by lazy { DataBoundArrayAdapter(requireContext(), R.layout.navigation_listitem, viewModel.autocompleteResults, null, filter) }
	val navParser by lazy { NavigationParser(AndroidGeocoderSearcher(requireContext().applicationContext), URLRedirector()) }
	val navTrigger by lazy { NavigationTriggerDeterminator(requireContext().applicationContext) }
	val controller by lazy { NavigationSearchController(lifecycleScope, navParser, placeSearch, navTrigger, viewModel) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val binding = NavigationStatusBindingImpl.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.viewModel = viewModel
		binding.controller = controller

		// when query field changes, clear the SearchFailed status
		viewModel.query.observe(viewLifecycleOwner) {
			viewModel.searchFailed.value = false
		}

		// autocomplete behaviors
		val autocomplete = binding.root.findViewById<AutoCompleteTextView>(R.id.txtNavigationAddress)
		autocomplete.setAdapter(adapter)
		autocomplete.onItemClickListener = AdapterView.OnItemClickListener { adapterView, _view, index, _id ->
			val item = adapterView.getItemAtPosition(index) as? MapResult
			item?.also { triggerNavigation(it) }
		}
		// when NavController updates the query with the full result address
		// it triggers the autocomplete popup, so close it
		viewModel.searchStatus.observe(viewLifecycleOwner) {
			autocomplete.dismissDropDown()
		}
		return binding.root
	}

	fun triggerNavigation(result: MapResult) {
		controller.startNavigation(result)
	}

	inner class NavSearchFilter(val api: MapPlaceSearch, val output: MutableList<MapResult>): Filter() {
		override fun performFiltering(constraint: CharSequence?): FilterResults {
			if (constraint?.isNotBlank() != true) {
				return FilterResults().also {
					it.count = 0
					it.values = ArrayList<MapResult>()
				}
			}
			return runBlocking {
				val results = api.searchLocationsAsync(constraint.toString()).await()
				FilterResults().also {
					it.count = results.size
					it.values = results
				}
			}
		}

		override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
			output.clear()
			val resultList = (results?.values as? List<*>)?.mapNotNull { it as? MapResult } ?: emptyList()
			output.addAll(resultList)
			adapter.notifyDataSetChanged()
		}
	}
}