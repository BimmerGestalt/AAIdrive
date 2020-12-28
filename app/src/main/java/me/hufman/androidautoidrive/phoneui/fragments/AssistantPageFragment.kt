package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_assistantpage.*
import me.hufman.androidautoidrive.PhoneAppResourcesAndroid
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.assistant.AssistantControllerAndroid

class AssistantPageFragment: Fragment() {
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_assistantpage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// phrase the page description based on the number of assistants
		val assistantController by lazy { AssistantControllerAndroid(requireContext(), PhoneAppResourcesAndroid(requireContext())) }
		val count = assistantController.getAssistants().size
		lblAssistantPage.text = resources.getQuantityText(R.plurals.lbl_assistantpage, count)
	}
}