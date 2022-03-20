package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.StoredList
import me.hufman.androidautoidrive.databinding.MapQuickDestinationsBinding
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundListAdapter
import me.hufman.androidautoidrive.phoneui.adapters.ReorderableItemsCallback
import me.hufman.androidautoidrive.phoneui.controllers.QuickEditListController

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
	val adapter by lazy { DataBoundListAdapter(destinations, R.layout.quickeditlist_listitem, controller) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val binding = MapQuickDestinationsBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.controller = controller
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		val listMapQuickDestinations = view.findViewById<RecyclerView>(R.id.listMapQuickDestinations)
		listMapQuickDestinations.layoutManager = LinearLayoutManager(requireActivity())
		listMapQuickDestinations.adapter = adapter
		itemTouchHelper.attachToRecyclerView(listMapQuickDestinations)  // enable drag/swipe
		controller.adapter = adapter        // allow the controller to notifyDataSetChanged
	}

	override fun onResume() {
		super.onResume()

		// load the list of items into the adapter
		adapter.notifyDataSetChanged()
	}
}