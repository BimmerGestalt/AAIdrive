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
import me.hufman.androidautoidrive.databinding.NotificationQuickRepliesBinding
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundListAdapter
import me.hufman.androidautoidrive.phoneui.adapters.ReorderableItemsCallback
import me.hufman.androidautoidrive.phoneui.controllers.QuickEditListController

class NotificationQuickRepliesFragment: Fragment() {
	// the model of data to show
	val appSettings by lazy { MutableAppSettingsReceiver(requireContext().applicationContext) }
	val replies by lazy { StoredList(appSettings, AppSettings.KEYS.NOTIFICATIONS_QUICK_REPLIES) }

	// swiping interactions
	val itemTouchCallback by lazy { ReorderableItemsCallback(replies) }
	val itemTouchHelper by lazy { ItemTouchHelper(itemTouchCallback) }

	// the controller to handle UI actions
	val controller by lazy { QuickEditListController(replies, itemTouchHelper) }

	// the RecyclerView.Adapter to display
	val adapter by lazy { DataBoundListAdapter(replies, R.layout.quickeditlist_listitem, controller) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val binding = NotificationQuickRepliesBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.controller = controller
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		val listNotificationQuickReplies = view.findViewById<RecyclerView>(R.id.listNotificationQuickReplies)
		listNotificationQuickReplies.layoutManager = LinearLayoutManager(requireActivity())
		listNotificationQuickReplies.adapter = adapter
		itemTouchHelper.attachToRecyclerView(listNotificationQuickReplies)  // enable drag/swipe
		controller.adapter = adapter        // allow the controller to notifyDataSetChanged
	}

	override fun onResume() {
		super.onResume()

		// load the list of items into the adapter
		adapter.notifyDataSetChanged()
	}
}
