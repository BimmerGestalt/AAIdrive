package me.hufman.androidautoidrive.phoneui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_supportpage.*
import me.hufman.androidautoidrive.BuildConfig
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.DonationRequest
import java.text.SimpleDateFormat
import java.util.*

class SupportPageFragment: Fragment() {
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_supportpage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		btn_donations.setOnClickListener {
			val intent = Intent(Intent.ACTION_VIEW).apply {
				data = Uri.parse(DonationRequest.DONATION_URL)
				flags = Intent.FLAG_ACTIVITY_NEW_TASK
			}
			requireContext().startActivity(intent)
		}

		btn_support_feedback.setOnClickListener {
			val intent = Intent(Intent.ACTION_VIEW).apply {
				data = Uri.parse("https://github.com/hufman/AndroidAutoIdrive/discussions")
				flags = Intent.FLAG_ACTIVITY_NEW_TASK
			}
			requireContext().startActivity(intent)
		}

		btn_support_issues.setOnClickListener {
			val intent = Intent(Intent.ACTION_VIEW).apply {
				data = Uri.parse("https://github.com/hufman/AndroidAutoIdrive/issues")
				flags = Intent.FLAG_ACTIVITY_NEW_TASK
			}
			requireContext().startActivity(intent)
		}

		btn_support_share.setOnClickListener {
			val intent = Intent(Intent.ACTION_VIEW).apply {
				data = Uri.parse("https://github.com/hufman/AndroidAutoIdrive")
				flags = Intent.FLAG_ACTIVITY_NEW_TASK
			}
			requireContext().startActivity(intent)
		}

		val buildTime = SimpleDateFormat.getDateTimeInstance().format(Date(BuildConfig.BUILD_TIME))
		txtBuildInfo.text = getString(R.string.txt_build_info, BuildConfig.VERSION_NAME, buildTime)
	}
}