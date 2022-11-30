package me.hufman.androidautoidrive.phoneui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.R

class AddonsPageFragment: Fragment() {
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_addonpage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		view.findViewById<Button>(R.id.btnLearn).setOnClickListener {
			val intent = Intent(Intent.ACTION_VIEW).apply {
				data = Uri.parse("https://github.com/BimmerGestalt/IDriveConnectAddons")
				flags = Intent.FLAG_ACTIVITY_NEW_TASK
			}
			startActivity(intent)
		}
	}
}