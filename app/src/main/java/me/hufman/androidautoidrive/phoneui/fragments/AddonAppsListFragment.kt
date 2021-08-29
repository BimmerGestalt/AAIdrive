package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.NestedListView
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundArrayAdapter
import me.hufman.androidautoidrive.phoneui.controllers.AddonAppListController
import me.hufman.androidautoidrive.phoneui.controllers.PermissionsController
import me.hufman.androidautoidrive.phoneui.viewmodels.AddonsViewModel

class AddonAppsListFragment: Fragment() {
	val permissionsController by lazy { PermissionsController(requireActivity()) }
	val controller by lazy { AddonAppListController(requireActivity(), permissionsController) }
	val viewModel by viewModels<AddonsViewModel> { AddonsViewModel.Factory(requireContext()) }
	val adapter by lazy {
		DataBoundArrayAdapter(requireContext(), R.layout.addonapp_listitem, viewModel.apps, controller)
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_addon_applist, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		view.findViewById<NestedListView>(R.id.listAddonApps).also {
			it.emptyView = view.findViewById(R.id.txtEmptyAddonApps)
			it.adapter = adapter
		}
	}

	override fun onResume() {
		super.onResume()
		viewModel.update()
		adapter.notifyDataSetChanged()
	}
}