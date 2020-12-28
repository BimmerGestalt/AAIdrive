package me.hufman.androidautoidrive.phoneui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_musicpage.*
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.phoneui.UIState
import me.hufman.androidautoidrive.phoneui.visible

class MusicPageFragment: Fragment() {
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_musicpage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		btnGrantSessions.setOnClickListener {
			promptNotificationPermission()
		}
	}

	override fun onResume() {
		super.onResume()
		redraw()
	}

	fun redraw() {
		paneGrantSessions.visible = !hasNotificationPermission()
	}

	fun promptNotificationPermission() {
		startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
	}

	fun hasNotificationPermission(): Boolean {
		return UIState.notificationListenerConnected && NotificationManagerCompat.getEnabledListenerPackages(requireContext()).contains(requireContext().packageName)
	}
}