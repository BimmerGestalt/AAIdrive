package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_musicpage.*
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.phoneui.controllers.PermissionsController
import me.hufman.androidautoidrive.phoneui.viewmodels.PermissionsModel
import me.hufman.androidautoidrive.phoneui.visible

class MusicPageFragment: Fragment() {
	val permissionsController by lazy { PermissionsController(requireActivity()) }
	val viewModel by lazy { PermissionsModel.Factory(requireContext().applicationContext).create(PermissionsModel::class.java) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_musicpage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		btnGrantSessions.setOnClickListener {
			permissionsController.promptNotification()
		}

		viewModel.hasNotificationPermission.observe(viewLifecycleOwner) {
			paneGrantSessions.visible = !it
		}
	}

	override fun onResume() {
		super.onResume()
		viewModel.update()
	}
}