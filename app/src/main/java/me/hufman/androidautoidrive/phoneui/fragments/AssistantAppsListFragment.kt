package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_assistant_applist.*
import me.hufman.androidautoidrive.PhoneAppResourcesAndroid
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.assistant.AssistantAppInfo
import me.hufman.androidautoidrive.carapp.assistant.AssistantControllerAndroid
import me.hufman.androidautoidrive.phoneui.visible

class AssistantAppsListFragment: Fragment() {
	val assistantController by lazy { AssistantControllerAndroid(requireContext(), PhoneAppResourcesAndroid(requireContext())) }
	val displayedAssistantApps = ArrayList<AssistantAppInfo>()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_assistant_applist, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		listAssistantApps.emptyView = txtEmptyAssistantApps

		displayedAssistantApps.clear()
		displayedAssistantApps.addAll(assistantController.getAssistants().toList().sortedBy { it.name })
		listAssistantApps.adapter = object: ArrayAdapter<AssistantAppInfo>(requireContext(), R.layout.assistantapp_listitem, displayedAssistantApps) {
			override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
				val appInfo = getItem(position)
				val layout = convertView ?: layoutInflater.inflate(R.layout.assistantapp_listitem, parent,false)
				return if (appInfo != null) {
					layout.findViewById<ImageView>(R.id.imgAssistantAppIcon).setImageDrawable(appInfo.icon)
					layout.findViewById<ImageView>(R.id.imgAssistantAppIcon).contentDescription = appInfo.name
					layout.findViewById<TextView>(R.id.txtAssistantAppName).text = appInfo.name
					layout.findViewById<ImageView>(R.id.imgAssistantSettingsIcon).visible = assistantController.supportsSettings(appInfo)

					layout.findViewById<ImageView>(R.id.imgAssistantSettingsIcon).setOnClickListener {
						assistantController.openSettings(appInfo)
					}
					layout
				} else {
					layout.findViewById<TextView>(R.id.txtAssistantAppName).text = getText(R.string.lbl_error)
					layout
				}
			}
		}
		listAssistantApps.setOnItemClickListener { adapterView, _, i, _ ->
			val appInfo = adapterView.adapter.getItem(i) as? AssistantAppInfo
			if (appInfo != null) {
				assistantController.triggerAssistant(appInfo)
			}
		}
	}
}