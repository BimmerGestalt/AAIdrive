package me.hufman.androidautoidrive.phoneui

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Point
import android.os.Bundle
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.activity_navintent.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.navigation.AndroidGeocoderSearcher
import me.hufman.androidautoidrive.carapp.navigation.NavigationParser
import me.hufman.androidautoidrive.carapp.navigation.NavigationTriggerSender
import me.hufman.androidautoidrive.carapp.navigation.URLRedirector
import me.hufman.androidautoidrive.phoneui.ViewHelpers.visible
import me.hufman.androidautoidrive.phoneui.controllers.NavigationSearchController
import me.hufman.androidautoidrive.phoneui.viewmodels.NavigationStatusModel

class NavIntentActivity: AppCompatActivity() {
	companion object {
		val TAG = "NavActivity"

		val URL_MATCHER = Regex("(https?|geo|google.navigation)://[^ ]*")
	}

	val viewModel by viewModels<NavigationStatusModel> { NavigationStatusModel.Factory(this.applicationContext) }

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		val params = window.decorView.layoutParams as WindowManager.LayoutParams
		params.alpha = 1f
		params.dimAmount = 0.2f
		window.attributes = params

		val display = windowManager.defaultDisplay
		val size = Point()
		display.getSize(size)

		window.setLayout((0.7 * size.x).toInt(), (0.5 * size.y).toInt())
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_navintent)

		viewModel.isSearching.observe(this) {
			prgNavSpinner.isIndeterminate = it

			// finished searching
			if (!it) {
				prgNavSpinner.progress = 0

				val animation = ObjectAnimator.ofInt(prgNavSpinner, "progress", prgNavSpinner.progress, prgNavSpinner.max)
				animation.duration = 1000
				animation.interpolator = DecelerateInterpolator()
				animation.start()
			}
		}
		viewModel.searchFailed.observe(this) {
			txtNavError.visible = it
		}
		viewModel.searchStatus.observe(this) {
			val oldText = txtNavLabel.text
			val newText = this.run(it)

			// don't clear the "Parsing Failed" message
			val clearingAfterError = newText.isBlank() && viewModel.searchFailed.value == true
			if (!clearingAfterError) {
				// close the window when the message is cleared by the controller
				if (oldText.isNotBlank() && newText.isBlank()) {
					finish()
				} else {
					// otherwise update the label
					txtNavLabel.text = newText
				}
			}
		}
	}

	private fun decodeTextQuery(query: String?): String? {
		query ?: return null
		return URL_MATCHER.find(query)?.let { it.value }
	}

	override fun onResume() {
		super.onResume()
		val query = when(intent?.action) {
			Intent.ACTION_VIEW -> intent?.dataString
			Intent.ACTION_SEND -> decodeTextQuery(intent?.getStringExtra(Intent.EXTRA_TEXT))
			else -> null
		}
		if (query != null) {
			txtNavError.text = query.toString()       // in case we need to show it for parse errors
			val navParser = NavigationParser(AndroidGeocoderSearcher(this.applicationContext), URLRedirector())
			val navTrigger = NavigationTriggerSender(this.applicationContext)
			val controller = NavigationSearchController(lifecycleScope, navParser, navTrigger, viewModel)
			controller.startNavigation(query.toString())
		}
	}
}