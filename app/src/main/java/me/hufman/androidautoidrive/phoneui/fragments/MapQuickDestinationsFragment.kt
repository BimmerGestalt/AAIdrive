package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AutoCompleteTextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.StoredList
import me.hufman.androidautoidrive.databinding.MapQuickDestinationsBinding
import me.hufman.androidautoidrive.maps.MapPlaceSearch
import me.hufman.androidautoidrive.maps.PlaceSearchProvider
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundArrayAdapter
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundListAdapter
import me.hufman.androidautoidrive.phoneui.adapters.ReorderableItemsCallback
import me.hufman.androidautoidrive.phoneui.controllers.NavSearchFilter
import me.hufman.androidautoidrive.phoneui.controllers.QuickEditListController
import me.hufman.androidautoidrive.phoneui.viewmodels.MapResultViewModel

class MapQuickDestinationsFragment: Fragment() {
	// the model of data to show
	val appSettings by lazy { MutableAppSettingsReceiver(requireContext().applicationContext) }
	val destinations by lazy { StoredList(appSettings, AppSettings.KEYS.MAP_QUICK_DESTINATIONS) }

	// swiping interactions
	val itemTouchCallback by lazy { ReorderableItemsCallback(destinations) }
	val itemTouchHelper by lazy { ItemTouchHelper(itemTouchCallback) }

	// the controller to handle UI actions
	val controller by lazy { QuickEditListController(destinations, itemTouchHelper) }

	// the RecyclerView.Adapter to display
	val destinationsAdapter by lazy { DataBoundListAdapter(destinations, R.layout.quickeditlist_listitem, controller) }

	// autocomplete adapters
	val placeSearch by lazy { PlaceSearchProvider(requireContext()).getInstance() }
	val autocompleteResults = ArrayList<MapResultViewModel>()
	val autocompleteFilter by lazy { MapQuickDestinationSearchFilter(placeSearch, autocompleteResults) }
	val autocompleteAdapter by lazy { DataBoundArrayAdapter(requireContext(), R.layout.navigation_listitem, autocompleteResults, null, autocompleteFilter) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val binding = MapQuickDestinationsBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.controller = controller

		// autocomplete behaviors
		val autocomplete = binding.root.findViewById<AutoCompleteTextView>(R.id.txtInput)
		autocomplete.setAdapter(autocompleteAdapter)
		autocomplete.onItemClickListener = AdapterView.OnItemClickListener { adapterView, _, index, _ ->
			val item = adapterView.getItemAtPosition(index) as? MapResultViewModel
			item?.also {
				controller.currentInput.value = it.result.toString()
				controller.addItem()
			}
		}

		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		val listMapQuickDestinations = view.findViewById<RecyclerView>(R.id.listMapQuickDestinations)
		listMapQuickDestinations.layoutManager = LinearLayoutManager(requireActivity())
		listMapQuickDestinations.adapter = destinationsAdapter
		itemTouchHelper.attachToRecyclerView(listMapQuickDestinations)  // enable drag/swipe
		controller.adapter = destinationsAdapter        // allow the controller to notifyDataSetChanged
	}

	override fun onResume() {
		super.onResume()

		// load the list of items into the adapter
		destinationsAdapter.notifyDataSetChanged()
	}


	inner class MapQuickDestinationSearchFilter(api: MapPlaceSearch, output: MutableList<MapResultViewModel>): NavSearchFilter(api, output) {
		override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
			super.publishResults(constraint, results)
			autocompleteAdapter.notifyDataSetChanged()
		}
	}
}