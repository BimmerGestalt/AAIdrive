package me.hufman.androidautoidrive.phoneui.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.TAG
import me.hufman.androidautoidrive.databinding.NotificationPageBinding
import me.hufman.androidautoidrive.phoneui.controllers.NotificationPageController
import me.hufman.androidautoidrive.phoneui.controllers.PermissionsController
import me.hufman.androidautoidrive.phoneui.viewmodels.NotificationSettingsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.PermissionsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.viewModels

class NotificationPageFragment: Fragment() {
	companion object {
		const val NOTIFICATION_CHANNEL_ID = "TestNotification"
		const val NOTIFICATION_ID = 169
	}

	val notificationSettingsModel by viewModels<NotificationSettingsModel> {NotificationSettingsModel.Factory(requireContext().applicationContext)}
	val permissionsModel by viewModels<PermissionsModel> {PermissionsModel.Factory(requireContext().applicationContext)}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val permissionsController by lazy { PermissionsController(requireActivity()) }
		val notificationPageController by lazy { NotificationPageController(notificationSettingsModel, permissionsModel, permissionsController) }

		val binding = NotificationPageBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.settingsModel = notificationSettingsModel
		binding.controller = notificationPageController
		return binding.root
	}

	override fun onResume() {
		super.onResume()
		// update the model, used in the controller to know to show the prompt
		permissionsModel.update()
	}

	class CustomActionListener: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			context ?: return
			intent ?: return
			if (RemoteInput.getResultsFromIntent(intent) != null) {
				val reply = RemoteInput.getResultsFromIntent(intent)
				Log.i(TAG, "Received reply")
				Toast.makeText(context, "Reply: ${reply?.getCharSequence("reply")}", Toast.LENGTH_SHORT).show()

				// seems to not work, oh well
				val manager = NotificationManagerCompat.from(context)
				manager.cancel(NOTIFICATION_ID)
			} else {
				Log.i(TAG, "Received custom action")
				Toast.makeText(context, "Custom Action press", Toast.LENGTH_SHORT).show()
				val manager = NotificationManagerCompat.from(context)
				manager.cancel(NOTIFICATION_ID)
			}
		}
	}
}