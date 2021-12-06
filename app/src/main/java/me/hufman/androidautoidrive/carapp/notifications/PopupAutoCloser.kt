package me.hufman.androidautoidrive.carapp.notifications

import android.os.Handler
import me.hufman.androidautoidrive.carapp.notifications.views.PopupView

class PopupAutoCloser(val handler: Handler, val popupView: PopupView) {
	companion object {
		const val DELAY = 10000L
	}

	val runnable = Runnable {
		popupView.hideNotification()
	}

	fun start() {
		handler.removeCallbacks(runnable)
		handler.postDelayed(runnable, DELAY)
	}
}