package me.hufman.androidautoidrive.phoneui.controllers

import android.content.Intent
import android.net.Uri
import android.view.View
import me.hufman.androidautoidrive.phoneui.DonationRequest

class SupportPageController {
	fun onClickDonations(view: View) {
		val intent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse(DonationRequest.DONATION_URL)
			flags = Intent.FLAG_ACTIVITY_NEW_TASK
		}
		view.context.startActivity(intent)
	}
	fun onClickFeedback(view: View) {
		val intent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse("https://github.com/BimmerGestalt/AAIdrive/discussions")
			flags = Intent.FLAG_ACTIVITY_NEW_TASK
		}
		view.context.startActivity(intent)
	}
	fun onClickIssues(view: View) {
		val intent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse("https://github.com/BimmerGestalt/AAIdrive/issues")
			flags = Intent.FLAG_ACTIVITY_NEW_TASK
		}
		view.context.startActivity(intent)
	}
	fun onClickShare(view: View) {
		val intent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse("https://github.com/BimmerGestalt/AAIdrive")
			flags = Intent.FLAG_ACTIVITY_NEW_TASK
		}
		view.context.startActivity(intent)
	}
	fun onClickPrivacy(view: View) {
		val intent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse("https://github.com/BimmerGestalt/AAIdrive#privacy")
			flags = Intent.FLAG_ACTIVITY_NEW_TASK
		}
		view.context.startActivity(intent)
	}
}