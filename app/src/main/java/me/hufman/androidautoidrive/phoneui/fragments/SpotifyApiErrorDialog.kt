package me.hufman.androidautoidrive.phoneui.fragments

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import me.hufman.androidautoidrive.R
import java.lang.IllegalStateException

class SpotifyApiErrorDialog: DialogFragment() {
	companion object {
		const val EXTRA_CLASSNAME = "classname"
		const val EXTRA_MESSAGE = "message"
	}
	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val excClassname = arguments?.getString(EXTRA_CLASSNAME) ?: ""
		val errorMessage = arguments?.getString(EXTRA_MESSAGE) ?: ""
		val hint = when(excClassname) {
			"CouldNotFindSpotifyApp" -> getString(R.string.musicAppNotes_spotify_apiNotFound)
			"UserNotAuthorizedException" -> if (errorMessage.contains("AUTHENTICATION_SERVICE_UNAVAILABLE")) {
				getString(R.string.musicAppNotes_spotify_apiUnavailable)
			} else ""
			else -> ""
		}
		val message = "$excClassname\n$errorMessage\n\n$hint"
		return activity?.let {
			AlertDialog.Builder(it)
					.setMessage(message)
					.setPositiveButton(R.string.lbl_ok) { _, _ -> }
					.create()
		} ?: throw IllegalStateException("Activity cannot be null")
	}
}