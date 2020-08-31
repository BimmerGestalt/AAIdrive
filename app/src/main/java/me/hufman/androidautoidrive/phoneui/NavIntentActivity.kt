package me.hufman.androidautoidrive.phoneui

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Point
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlinx.android.synthetic.main.activity_navintent.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.NavigationTrigger.Companion.parseUrl
import me.hufman.androidautoidrive.carapp.NavigationTriggerSender

class NavIntentActivity: Activity() {
	companion object {
		val TAG = "NavActivity"
		val INTENT_NAV_SUCCESS = "me.hufman.androidautoidrive.NavIntentActivity.SUCCESS"

		val TIMEOUT = 8000L
		val SUCCESS = 1000L
	}

	var listening = false
	val successListener = object: BroadcastReceiver() {
		override fun onReceive(p0: Context?, p1: Intent?) {
			if (p1?.action == INTENT_NAV_SUCCESS) {
				onSuccess()
			}
		}
	}

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
	}

	override fun onResume() {
		super.onResume()
		val url = intent?.data
		if (url != null) {
			val rhmiNavigationData = parseUrl(url.toString())
			Log.i(TAG, "Parsing Nav Uri $url to $rhmiNavigationData initiate IDrive navigation")
			if (rhmiNavigationData != null) {
				NavigationTriggerSender(this).triggerNavigation(rhmiNavigationData)
				onBegin()
			} else {
				onParseFailure()
			}
		}
	}

	fun onBegin() {
		registerReceiver(successListener, IntentFilter(INTENT_NAV_SUCCESS))
		listening = true

		txtNavLabel.text = getText(R.string.lbl_navigation_listener_pending)
		txtNavError.text = ""
		prgNavSpinner.visible = true
		prgNavSpinner.isIndeterminate = true

		Handler().postDelayed({
			finish()
		}, TIMEOUT)
	}

	fun onParseFailure() {
		txtNavLabel.text = getString(R.string.lbl_navigation_listener_parsefailure)
		txtNavError.text = intent?.data?.toString() ?: ""
		prgNavSpinner.visible = false
		prgNavSpinner.isIndeterminate = false
		prgNavSpinner.progress = 0
	}

	fun onSuccess() {
		txtNavLabel.text = getText(R.string.lbl_navigation_listener_success)
		prgNavSpinner.isIndeterminate = false
		prgNavSpinner.progress = 0

		val animation = ObjectAnimator.ofInt(prgNavSpinner, "progress", prgNavSpinner.progress, prgNavSpinner.max)
		animation.duration = 500
		animation.interpolator = DecelerateInterpolator()
		animation.start()

		Handler().postDelayed({
			finish()
		}, SUCCESS)
	}

	override fun onPause() {
		super.onPause()
		if (listening) {
			unregisterReceiver(successListener)
			listening = false
		}
	}
}