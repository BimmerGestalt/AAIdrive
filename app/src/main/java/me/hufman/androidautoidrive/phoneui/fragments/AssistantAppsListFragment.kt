package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.PhoneAppResourcesAndroid
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.assistant.AssistantAppInfo
import me.hufman.androidautoidrive.carapp.assistant.AssistantControllerAndroid
import me.hufman.androidautoidrive.phoneui.NestedListView
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundArrayAdapter

class AssistantAppsListFragment: Fragment() {
	val assistantController by lazy { AssistantControllerAndroid(requireContext(), PhoneAppResourcesAndroid(requireContext())) }
	val displayedAssistantApps = ArrayList<AssistantAppInfo>()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_assistant_applist, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		displayedAssistantApps.clear()
		displayedAssistantApps.addAll(assistantController.getAssistants().toList().sortedBy { it.name })

		val listAssistantApps = view.findViewById<NestedListView>(R.id.listAssistantApps)
		listAssistantApps.emptyView = view.findViewById(R.id.txtEmptyAssistantApps)
		listAssistantApps.adapter = DataBoundArrayAdapter(requireContext(), R.layout.assistantapp_listitem, displayedAssistantApps, assistantController)
	}
}