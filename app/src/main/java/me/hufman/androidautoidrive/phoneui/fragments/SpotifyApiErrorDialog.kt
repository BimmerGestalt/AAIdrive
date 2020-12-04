package me.hufman.androidautoidrive.phoneui.fragments

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.spotify.authentication.AuthorizationActivity

class SpotifyApiErrorDialog: DialogFragment() {
	companion object {
		const val TAG = "SpotifyApiErrorDialog"
		const val EXTRA_CLASSNAME = "classname"
		const val EXTRA_MESSAGE = "message"
		const val EXTRA_WEB_API_AUTHORIZED = "webApiAuthorized"
	}

	lateinit var webApiMsgTextView: TextView
	lateinit var authorizeButton: Button

	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val builder = AlertDialog.Builder(activity)
		val view = activity?.layoutInflater?.inflate(R.layout.dialog_error, null)!!
		builder.setView(view)

		webApiMsgTextView = view.findViewById(R.id.txtWebApiAuthorizationMsg)
		authorizeButton = view.findViewById(R.id.btnAuthorizeWebApi)

		updateAppRemoteErrorComponents(view)
		updateWebApiAuthorizationComponents()

		view.findViewById<Button>(R.id.btnOk)?.setOnClickListener { dismiss() }

		return builder.create()
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (requestCode == AuthorizationActivity.REQUEST_CODE_SPOTIFY_LOGIN) {
			if (resultCode == Activity.RESULT_OK) {
				when(data?.getIntExtra(AuthorizationActivity.EXTRA_AUTHORIZATION_RESULT, Int.MAX_VALUE)) {
					AuthorizationActivity.AUTHORIZATION_CANCELED -> {
						webApiMsgTextView.text = getString(R.string.txt_spotify_api_authorization_canceled)
					}

					AuthorizationActivity.AUTHORIZATION_FAILED -> {
						webApiMsgTextView.text = getString(R.string.txt_spotify_api_authorization_failed)
					}

					AuthorizationActivity.AUTHORIZATION_REFUSED -> {
						webApiMsgTextView.text = getString(R.string.txt_spotify_api_authorization_refused)
					}

					AuthorizationActivity.AUTHORIZATION_SUCCESS -> {
						webApiMsgTextView.text = getString(R.string.txt_spotify_api_authorization_success)
						authorizeButton.visible = false
					}

					else -> {
						Log.d(TAG, "Unknown authorization activity result")
					}
				}
			}
		}
	}

	/**
	 * Updates dialog components with the Spotify App Remote error message if one is present.
	 */
	private fun updateAppRemoteErrorComponents(view: View) {
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
		val appRemoteErrorTextView = view.findViewById<TextView>(R.id.txtAppRemoteError)
		appRemoteErrorTextView?.text = if (excClassname.isNotBlank()) {
			message
		} else {
			""
		}
		val spotifyAppRemoteLastErrorTitleTextView = view.findViewById<TextView>(R.id.txtSpotifyAppRemoteLastErrorTitle)
		spotifyAppRemoteLastErrorTitleTextView?.visible = !appRemoteErrorTextView?.text.isNullOrBlank()
	}

	/**
	 * Updates the dialog components with the Spotify Web API authorization state and possible actions.
	 */
	private fun updateWebApiAuthorizationComponents() {
		val isWebApiAuthorized = arguments?.getBoolean(EXTRA_WEB_API_AUTHORIZED)
		if (isWebApiAuthorized == false) {
			webApiMsgTextView.text = getString(R.string.txt_spotify_api_authorization_needed)

			authorizeButton.visible = true
			authorizeButton.setOnClickListener { launchAuthorizationActivity() }
		} else {
			webApiMsgTextView.text = getString(R.string.txt_spotify_api_authorization_success)
			authorizeButton.visible = false
		}
	}

	/**
	 * Launches an activity displaying the the authorization login page in a custom tab. This is the
	 * entry point of the authorization process.
	 */
	private fun launchAuthorizationActivity() {
		val intent = Intent(context, AuthorizationActivity::class.java)
		startActivityForResult(intent, AuthorizationActivity.REQUEST_CODE_SPOTIFY_LOGIN)
	}
}